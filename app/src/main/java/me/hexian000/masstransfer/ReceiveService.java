package me.hexian000.masstransfer;

import android.app.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.widget.Toast;
import me.hexian000.masstransfer.io.DirectoryWriter;
import me.hexian000.masstransfer.io.Pipe;
import me.hexian000.masstransfer.io.RateCounter;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Timer;
import java.util.TimerTask;

import static me.hexian000.masstransfer.TransferApp.CHANNEL_TRANSFER_STATE;
import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

public class ReceiveService extends Service {
	Handler handler = new Handler();
	Notification.Builder builder;
	int startId = 0;
	NotificationManager notificationManager = null;
	ReceiveThread thread = null;
	DocumentFile root = null;
	DiscoverService mService;
	boolean result;
	private ServiceConnection mConnection;

	private void initNotification() {
		if (builder == null) {
			builder = new Notification.Builder(this.getApplicationContext());
		}
		builder.setContentIntent(null)
				.setContentTitle(getResources().getString(R.string.notification_receiving))
				.setSmallIcon(R.drawable.ic_send_black_24dp)
				.setWhen(System.currentTimeMillis())
				.setProgress(0, 0, true)
				.setOngoing(true)
				.setVisibility(Notification.VISIBILITY_PUBLIC);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// Android 8.0+
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (manager != null) {
				TransferApp.createNotificationChannels(manager, getResources());
				builder.setChannelId(CHANNEL_TRANSFER_STATE);
			}
		} else {
			// Android 7.1
			builder.setPriority(Notification.PRIORITY_DEFAULT).setLights(0, 0, 0).setVibrate(null).setSound(null);
		}
		Intent cancel = new Intent(this, ReceiveService.class);
		cancel.setAction("cancel");
		builder.addAction(new Notification.Action.Builder(null, getResources().getString(R.string.cancel),
				PendingIntent.getService(this, startId, cancel, 0)).build())
				.setContentText(getResources().getString(R.string.notification_starting));
	}

	private void stop() {
		if (thread != null) {
			thread.interrupt();
			thread = null;
		}
		notificationManager = null;
		builder = null;
		stopSelf();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ("cancel".equals(intent.getAction())) {
			Log.d(LOG_TAG, "ReceiveService user cancelled");
			stop();
			return START_NOT_STICKY;
		}

		this.startId = startId;
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		initNotification();
		Notification notification = builder.build();
		startForeground(startId, notification);

		root = DocumentFile.fromTreeUri(this, intent.getData());

		thread = new ReceiveThread();
		thread.start();

		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		final TransferApp app = (TransferApp) getApplicationContext();
		app.receiveService = this;
		result = false;
		mConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName className, IBinder service) {
				DiscoverService.Binder binder = (DiscoverService.Binder) service;
				mService = binder.getService();
			}

			@Override
			public void onServiceDisconnected(ComponentName arg0) {
				mService = null;
			}
		};
		Intent discover = new Intent(this, DiscoverService.class);
		bindService(discover, mConnection, Context.BIND_AUTO_CREATE);
		Log.d(LOG_TAG, "bind DiscoverService in ReceiveService");
		MainActivity mainActivity = app.mainActivity;
		if (mainActivity != null) {
			mainActivity.handler.post(mainActivity::updateReceiveButton);
		}
	}

	@Override
	public void onDestroy() {
		if (mService != null) {
			unbindService(mConnection);
			mService = null;
			Log.d(LOG_TAG, "unbind DiscoverService in ReceiveService");
		}
		MainActivity mainActivity = ((TransferApp) getApplicationContext()).mainActivity;
		if (mainActivity != null) {
			mainActivity.handler.post(mainActivity::updateReceiveButton);
		}
		if (result) {
			Toast.makeText(this, R.string.transfer_success, Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(this, R.string.transfer_failed, Toast.LENGTH_SHORT).show();
		}
		((TransferApp) getApplicationContext()).receiveService = null;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException();
	}

	private class ReceiveThread extends Thread {
		final Object lock = new Object();
		ServerSocket listener = null;
		Socket socket = null;

		ReceiveThread() {
			super();
		}

		@Override
		public void interrupt() {
			synchronized (lock) {
				if (listener != null) {
					try {
						listener.close();
					} catch (IOException ignored) {
					}
				}
				if (socket != null) {
					try {
						socket.close();
					} catch (IOException ignored) {
					}
				}
			}
			super.interrupt();
		}

		@Override
		public void run() {
			try {
				synchronized (lock) {
					listener = new ServerSocket(TransferApp.TCP_PORT);
				}
				Log.d(LOG_TAG, "ReceiveService begins to listen");
				listener.setSoTimeout(1000); // prevent thread leak
				while (!thread.isInterrupted()) {
					try {
						Socket s = listener.accept();
						synchronized (lock) {
							socket = s;
						}
						break;
					} catch (SocketTimeoutException ignored) {
					}
				}
				if (socket == null) {
					return;
				}
				listener.close();
				synchronized (lock) {
					listener = null;
				}
				try {
					Log.d(LOG_TAG, "ReceiveService accepted connection");
					socket.setPerformancePreferences(0, 0, 1);
					socket.setSoTimeout(30000);
					runPipe(socket);
				} catch (SocketTimeoutException e) {
					Log.e(LOG_TAG, "socket timeout");
				} catch (IOException e) {
					Log.e(LOG_TAG, "pipe", e);
				} finally {
					try {
						socket.close();
					} catch (IOException e) {
						Log.e(LOG_TAG, "socket close failed", e);
					} finally {
						synchronized (lock) {
							socket = null;
						}
					}
				}
			} catch (InterruptedException e) {
				Log.d(LOG_TAG, "ReceiveService interrupted");
			} catch (Exception e) {
				Log.e(LOG_TAG, "ReceiveService unexpected exception", e);
			} finally {
				Log.d(LOG_TAG, "ReceiveService closing");
				handler.post(ReceiveService.this::stop);
			}
		}

		private void runPipe(Socket socket) throws InterruptedException, IOException {
			int memClass = 0;
			{
				final ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
				if (activityManager != null) {
					memClass = activityManager.getLargeMemoryClass();
				}
			}
			final int pipeSize = Math.min(memClass - 16, 8) * 1024 * 1024;
			Log.d(LOG_TAG, "receive buffer size: " + TransferApp.sizeToString(pipeSize));
			Pipe pipe = new Pipe(pipeSize);
			DirectoryWriter writer = new DirectoryWriter(getContentResolver(), root, pipe, (text, now, max) -> {
				if (text != null) {
					text += "\n";
					if (pipe.getSize() > pipeSize / 2) {
						text += getResources().getString(R.string.bottleneck_local);
					} else {
						text += getResources().getString(R.string.bottleneck_network);
					}
				} else {
					text = getResources().getString(R.string.notification_finishing);
				}
				final String contentText = text;
				handler.post(() -> {
					if (builder != null && notificationManager != null) {
						builder.setContentText(contentText)
								.setStyle(new Notification.BigTextStyle().bigText(contentText))
								.setProgress(max, now, max == now && now == 0);
						notificationManager.notify(startId, builder.build());
					}
				});
			});
			Thread writerThread = new Thread(writer);
			writerThread.start();
			Timer timer = new Timer();
			try (InputStream in = socket.getInputStream()) {
				RateCounter rate = new RateCounter();
				final int rateInterval = 2;
				timer.schedule(new TimerTask() {

					@Override
					public void run() {
						handler.post(() -> {
							if (builder != null && notificationManager != null) {
								builder.setSubText(TransferApp.sizeToString(rate.rate() / rateInterval) + "/s");
								notificationManager.notify(startId, builder.build());
							}
						});
					}
				}, rateInterval * 1000, rateInterval * 1000);
				while (true) {
					byte[] buffer = new byte[1024];
					int read = in.read(buffer);
					if (read == buffer.length) {
						pipe.write(buffer);
					} else if (read > 0) {
						byte[] data = new byte[read];
						System.arraycopy(buffer, 0, data, 0, read);
						pipe.write(data);
					} else {
						break;
					}
					rate.increase(read);
				}
				pipe.close();
				writerThread.join();
				result = writer.isSuccess();
				Log.d(LOG_TAG, "receive thread finished normally");
			} finally {
				timer.cancel();
				writerThread.interrupt();
			}
		}

	}
}
