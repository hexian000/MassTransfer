package me.hexian000.masstransfer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.widget.Toast;
import me.hexian000.masstransfer.streams.DirectoryWriter;
import me.hexian000.masstransfer.streams.Pipe;
import me.hexian000.masstransfer.streams.RateCounter;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Timer;
import java.util.TimerTask;

import static me.hexian000.masstransfer.TransferApp.CHANNEL_TRANSFER_STATE;
import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

public class ReceiveService extends Service implements Runnable {
	Notification.Builder builder;
	int startId = 0;
	NotificationManager notificationManager = null;
	Thread thread = null;
	DocumentFile root = null;
	DiscoverService mService;
	boolean result;
	private ServiceConnection mConnection;

	private void initNotification() {
		if (builder == null)
			builder = new Notification.Builder(
					this.getApplicationContext());
		builder.setContentIntent(null)
				.setContentTitle(getResources().getString(R.string.notification_receiving))
				.setSmallIcon(R.drawable.ic_send_black_24dp)
				.setWhen(System.currentTimeMillis())
				.setProgress(0, 0, true)
				.setOngoing(true)
				.setVisibility(Notification.VISIBILITY_PUBLIC);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// Android 8.0+
			NotificationManager manager =
					(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (manager != null) {
				TransferApp.createNotificationChannels(
						manager,
						getResources()
				);
				builder.setChannelId(CHANNEL_TRANSFER_STATE);
			}
		} else {
			// Android 7.1
			builder.setPriority(Notification.PRIORITY_DEFAULT)
					.setLights(0, 0, 0)
					.setVibrate(null)
					.setSound(null);
		}
		Intent cancel = new Intent(this, ReceiveService.class);
		cancel.setAction("cancel");
		builder.addAction(new Notification.Action.Builder(
				null, getResources().getString(R.string.cancel),
				PendingIntent.getService(this, startId, cancel, 0)
		).build()).setContentText(getResources().getString(R.string.notification_starting));
	}

	private void stop() {
		if (thread != null) {
			if (thread.isAlive())
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
			result = false;
			stop();
			return START_NOT_STICKY;
		}

		this.startId = startId;
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		initNotification();
		Notification notification = builder.build();
		startForeground(startId, notification);

		root = DocumentFile.fromTreeUri(this, intent.getData());

		thread = new Thread(this);
		thread.start();

		return START_NOT_STICKY;
	}

	@Override
	public void run() {
		try (ServerSocket listener = new ServerSocket(TransferApp.TCP_PORT)) {
			Log.d(LOG_TAG, "ReceiveService begins to listen");
			listener.setSoTimeout(4000); // prevent thread leak
			while (thread != null) {
				try (Socket socket = listener.accept()) {
					socket.setPerformancePreferences(0, 0, 1);
					socket.setReceiveBufferSize(8 * 1024 * 1024);
					socket.setSoLinger(true, 10);
					runPipe(socket);
				} catch (SocketTimeoutException e) {
					continue;
				} catch (IOException e) {
					result = false;
					Log.e(LOG_TAG, "listener accept error", e);
				}
				break;
			}
		} catch (IOException e) {
			result = false;
			Log.e(LOG_TAG, "listener init error", e);
		} finally {
			Log.d(LOG_TAG, "ReceiveService closed");
			stop();
		}
	}

	private void runPipe(Socket socket) {
		Pipe pipe = new Pipe(256 * 1024 * 1024);
		DirectoryWriter writer = new DirectoryWriter(
				getContentResolver(),
				root, pipe,
				(text, now, max) -> {
					if (builder != null && notificationManager != null) {
						if (text != null)
							builder.setContentText(text).
									setProgress(max, now, false);
						else
							builder.setContentText(getResources().getString(R.string.notification_finishing)).
									setProgress(0, 0, true);
						notificationManager.notify(startId, builder.build());
					}
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
					if (builder != null && notificationManager != null) {
						builder.setSubText(TransferApp.sizeToString(rate.rate() / rateInterval) + "/s");
						notificationManager.notify(startId, builder.build());
					}
				}
			}, rateInterval * 1000, rateInterval * 1000);
			while (true) {
				byte[] buffer = new byte[1024];
				int read = in.read(buffer);
				if (read == buffer.length)
					pipe.write(buffer);
				else if (read > 0) {
					byte[] data = new byte[read];
					System.arraycopy(buffer, 0, data, 0, read);
					pipe.write(data);
				} else break;
				rate.increase(read);
				{
					final long size = pipe.getSize() / 1024;
					if (size > 8 * 1024)
						Log.v(LOG_TAG, "pipe size=" + size + "KB");
				}
			}
			pipe.close();
			writerThread.join();
			Log.d(LOG_TAG, "ReceiveService finished normally");
		} catch (InterruptedException ignored) {
			result = false;
			Log.d(LOG_TAG, "ReceiveService interrupted");
		} catch (IOException e) {
			result = false;
			Log.e(LOG_TAG, "ReceiveService", e);
		} finally {
			timer.cancel();
			if (writerThread.isAlive()) {
				result = false;
				writerThread.interrupt();
			}
		}
	}

	@Override
	public void onCreate() {
		result = true;
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
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException();
	}
}
