package me.hexian000.masstransfer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import me.hexian000.masstransfer.streams.DirectoryReader;
import me.hexian000.masstransfer.streams.Pipe;
import me.hexian000.masstransfer.streams.StreamWindow;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static me.hexian000.masstransfer.TransferApp.*;


public class TransferService extends Service implements Runnable {
	Notification.Builder builder;
	int startId = 0;
	String host;
	Thread thread = null;
	DocumentFile root = null;
	NotificationManager notificationManager = null;

	private void initNotification() {
		if (builder == null)
			builder = new Notification.Builder(this.getApplicationContext());
		builder.setContentIntent(null)
				.setLargeIcon(BitmapFactory.decodeResource(getResources(),
						R.mipmap.ic_launcher))
				.setContentTitle(getResources().getString(R.string.notification_sending))
				.setSmallIcon(R.mipmap.ic_launcher)
				.setWhen(System.currentTimeMillis())
				.setProgress(100, 0, true)
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
		Intent cancel = new Intent(this, TransferService.class);
		cancel.setAction("cancel");
		builder.addAction(new Notification.Action.Builder(
				null, getResources().getString(R.string.cancel),
				PendingIntent.getService(this, startId, cancel, 0)
		).build()).
				setContentText(getResources().getString(R.string.notification_starting));
	}

	private void stop() {
		if (thread != null) {
			Log.d(LOG_TAG, "try interrupt transfer thread");
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
			Log.d(LOG_TAG, "TransferService user cancelled");
			stop();
			return START_NOT_STICKY;
		}

		this.startId = startId;
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		initNotification();
		Notification notification = builder.build();
		startForeground(startId, notification);

		host = intent.getAction();
		root = DocumentFile.fromTreeUri(this, intent.getData());
		thread = new Thread(this);
		thread.start();
		return START_NOT_STICKY;
	}

	@Override
	public void run() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(
					InetAddress.getByName(host),
					TCP_PORT), 4000);
			socket.setSendBufferSize(16 * 1024 * 1024);
			socket.setSoLinger(true, 30);
			socket.setSoTimeout(10 * 1000);
			runPipe(socket);
		} catch (IOException e) {
			Log.e(LOG_TAG, "connect failed", e);
			stopSelf();
		} finally {
			stop();
		}
	}

	private void runPipe(Socket socket) {
		Pipe pipe = new Pipe(16);
		DirectoryReader reader = new DirectoryReader(
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
		Thread readerThread = new Thread(reader);
		readerThread.start();
		Thread ackThread = null;
		StreamWindow window = new StreamWindow(64 * 1024 * 1024);
		try (InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
			ackThread = new Thread(() -> {
				try {
					while (true) {
						ByteBuffer ack = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN);
						int read = in.read(ack.array());
						if (read == 0 || read == -1) break;
						if (read != Long.BYTES) continue;
						long pos = ack.getLong();
						window.ack(pos);
					}
				} catch (IOException ignored) {
				}
				Log.d(LOG_TAG, "ack thread exited");
			});
			ackThread.start();
			while (true) {
				byte[] buffer = new byte[1024 * 1024];
				byte[] writeBuffer;
				int read = pipe.read(buffer);
				if (read == buffer.length) {
					writeBuffer = buffer;
				} else if (read > 0) {
					writeBuffer = new byte[read];
					System.arraycopy(buffer, 0, writeBuffer, 0, read);
				} else break;
				window.send(writeBuffer);
				out.write(writeBuffer);
			}
			readerThread.join();
			ackThread.join();
			Log.d(LOG_TAG, "TransferService finished normally");
		} catch (InterruptedException e) {
			Log.d(LOG_TAG, "TransferService interrupted");
		} catch (IOException e) {
			Log.e(LOG_TAG, "TransferService", e);
		} finally {
			if (ackThread != null && ackThread.isAlive()) {
				ackThread.interrupt();
			}
			if (readerThread.isAlive()) {
				readerThread.interrupt();
			}
		}
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException();
	}
}
