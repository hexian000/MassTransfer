package me.hexian000.masstransfer;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import me.hexian000.masstransfer.io.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Timer;
import java.util.TimerTask;

import static me.hexian000.masstransfer.TransferApp.*;

public class SendService extends TransferService {
	private String host;
	private String[] files = null;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ("cancel".equals(intent.getAction())) {
			Log.d(LOG_TAG, "SendService user cancelled");
			stop();
			return START_NOT_STICKY;
		}

		this.startId = startId;
		initNotification(R.string.notification_sending);

		Bundle extras = intent.getExtras();
		if (extras == null) {
			stopSelf();
			return START_NOT_STICKY;
		}
		host = extras.getString("host");
		files = extras.getStringArray("files");
		if (host == null || files == null) {
			stopSelf();
			return START_NOT_STICKY;
		}
		root = DocumentFile.fromTreeUri(this, intent.getData());

		thread = new TransferThread();
		thread.start();
		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		((TransferApp) getApplicationContext()).sendService = this;
	}

	@Override
	public void onDestroy() {
		showResultToast();
		((TransferApp) getApplicationContext()).sendService = null;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException();
	}

	private class TransferThread extends Thread {
		final Object lock = new Object();
		Socket socket = null;

		@Override
		public void interrupt() {
			synchronized (lock) {
				if (socket != null) {
					try {
						socket.setSoLinger(true, 0);
						socket.close();
					} catch (IOException e) {
						Log.e(LOG_TAG, "cancel: socket close failed", e);
					}
				}
			}
			super.interrupt();
		}

		@Override
		public void run() {
			try {
				synchronized (lock) {
					socket = new Socket();
				}
				socket.setPerformancePreferences(0, 0, 1);
				socket.setTrafficClass(IPTOS_THROUGHPUT);
				socket.setSendBufferSize(512 * 1024);
				socket.setSoTimeout(30000);
				socket.setSoLinger(true, 30);
				socket.connect(new InetSocketAddress(InetAddress.getByName(host), TCP_PORT), 4000);
				streamCopy(socket);
			} catch (SocketTimeoutException e) {
				Log.e(LOG_TAG, "socket timeout");
			} catch (IOException e) {
				Log.e(LOG_TAG, "connect failed", e);
			} finally {
				synchronized (lock) {
					if (socket != null) {
						try {
							socket.close();
						} catch (IOException e) {
							Log.e(LOG_TAG, "socket close failed", e);
						}
					}
					socket = null;
				}
				handler.post(SendService.this::stop);
			}
		}

		private void streamCopy(Socket socket) {
			final int bufferSize = 8 * 1024 * 1024;
			Buffer buffer = new Buffer(bufferSize);
			DirectoryReader reader = new DirectoryReader(getContentResolver(), root, files,
					new BufferOutputWrapper(buffer),
					(text, now, max) -> {
						if (text != null) {
							text += "\n";
							if (buffer.getSize() > bufferSize / 2) {
								text += getResources().getString(R.string.bottleneck_network);
							} else {
								text += getResources().getString(R.string.bottleneck_local);
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
			reader.start();
			Timer timer = new Timer();
			try (InputStream in = new BufferInputWrapper(buffer);
			     OutputStream out = socket.getOutputStream()) {
				RateCounter rate = new RateCounter();
				final int rateInterval = 5;
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
				reader.join();
				result = reader.isSuccess();
				Log.d(LOG_TAG, "SendService finished normally");
			} catch (InterruptedException e) {
				Log.d(LOG_TAG, "SendService interrupted");
			} catch (IOException e) {
				Log.e(LOG_TAG, "SendService", e);
			} finally {
				timer.cancel();
				reader.interrupt();
			}
		}
	}
}
