package me.hexian000.masstransfer;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import me.hexian000.masstransfer.io.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Timer;
import java.util.TimerTask;

import static me.hexian000.masstransfer.TransferApp.IPTOS_THROUGHPUT;
import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

public class ReceiveService extends TransferService {
	private DiscoverService mService;
	private ServiceConnection mConnection;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ("cancel".equals(intent.getAction())) {
			Log.d(LOG_TAG, "ReceiveService user cancelled");
			stop();
			return START_NOT_STICKY;
		}

		this.startId = startId;
		initNotification(R.string.notification_receiving);

		root = DocumentFile.fromTreeUri(this, intent.getData());

		thread = new ReceiveThread();
		thread.start();

		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		final TransferApp app = (TransferApp) getApplicationContext();
		app.receiveService = this;
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
		showResultToast();
		((TransferApp) getApplicationContext()).receiveService = null;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException();
	}

	private class ReceiveThread extends Thread {
		ServerSocket listener = null;
		Socket socket = null;

		@Override
		public void interrupt() {
			{
				final ServerSocket listener = this.listener;
				if (listener != null) {
					try {
						listener.close();
					} catch (IOException ignored) {
					}
				}
			}
			{
				final Socket socket = this.socket;
				if (socket != null) {
					try {
						socket.setSoLinger(true, 0);
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
				listener = new ServerSocket(TransferApp.TCP_PORT);
				Log.d(LOG_TAG, "ReceiveService begins to listen");
				listener.setSoTimeout(1000); // prevent thread leak
				listener.setPerformancePreferences(0, 0, 1);
				listener.setReceiveBufferSize(512 * 1024);
				while (!isInterrupted()) {
					try {
						socket = listener.accept();
						break;
					} catch (SocketTimeoutException ignored) {
					}
				}
				if (socket == null) {
					return;
				}
				listener.close();
				listener = null;
				try {
					Log.d(LOG_TAG, "ReceiveService accepted connection");
					socket.setPerformancePreferences(0, 0, 1);
					socket.setTrafficClass(IPTOS_THROUGHPUT);
					socket.setReceiveBufferSize(512 * 1024);
					socket.setSoTimeout(30000);
					streamCopy(socket);
				} catch (SocketTimeoutException e) {
					Log.e(LOG_TAG, "socket timeout");
				} catch (IOException e) {
					Log.e(LOG_TAG, "pipe", e);
				} finally {
					if (socket != null) {
						try {
							socket.close();
						} catch (IOException e) {
							Log.e(LOG_TAG, "socket close failed", e);
						}
						socket = null;
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

		private void streamCopy(Socket socket) throws InterruptedException, IOException {
			final int bufferSize = Math.max(TransferApp.HeapSize - 16 * 1024 * 1024, 8 * 1024 * 1024);
			Log.d(LOG_TAG, "receive buffer size: " + TransferApp.sizeToString(bufferSize));
			Buffer buffer = new Buffer(bufferSize);
			DirectoryWriter writer = new DirectoryWriter(getContentResolver(), root, new BufferInputWrapper(buffer),
					(text, now, max) -> {
						if (text != null) {
							text += "\n";
							if (buffer.getSize() > bufferSize / 2) {
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
			writer.start();
			Timer timer = new Timer();
			try (InputStream in = socket.getInputStream();
			     OutputStream out = new BufferOutputWrapper(buffer)) {
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
				byte[] packet = new byte[8 * 1024];
				int read;
				do {
					read = in.read(packet);
					if (read > 0) {
						out.write(packet, 0, read);
						rate.increase(read);
					}
				} while (read >= 0);
				out.flush();
				out.close();
				writer.join();
				result = writer.isSuccess();
				Log.d(LOG_TAG, "receive thread finished normally");
			} finally {
				timer.cancel();
				writer.interrupt();
			}
		}

	}
}
