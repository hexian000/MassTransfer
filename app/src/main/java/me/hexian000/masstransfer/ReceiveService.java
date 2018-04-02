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
import me.hexian000.masstransfer.streams.DirectoryWriter;
import me.hexian000.masstransfer.streams.Pipe;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static me.hexian000.masstransfer.TransferApp.CHANNEL_TRANSFER_STATE;
import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

public class ReceiveService extends Service implements Runnable {
	Thread thread = null;
	ServerSocket listener = null;
	DocumentFile root = null;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ("cancel".equals(intent.getAction())) {
			if (thread != null) {
				thread.interrupt();
				thread = null;
			}
			stopSelf();
			return super.onStartCommand(intent, flags, startId);
		}

		Notification.Builder builder = new Notification.Builder(
				this.getApplicationContext());
		builder.setContentIntent(null)
				.setLargeIcon(BitmapFactory.decodeResource(this.getResources(),
						R.mipmap.ic_launcher))
				.setContentTitle("接收状态")
				.setSmallIcon(R.mipmap.ic_launcher)
				.setContentText("<文件名>")
				.setWhen(System.currentTimeMillis())
				.setProgress(100, 0, true)
				.setOngoing(true)
				.setVisibility(Notification.VISIBILITY_PUBLIC);

		PendingIntent cancelIntent;

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
			Intent cancel = new Intent(this, ReceiveService.class);
			cancel.setAction("cancel");
			cancelIntent = PendingIntent.getForegroundService(this, startId, cancel, 0);
		} else {
			// Android 7.1
			Intent cancel = new Intent(this, ReceiveService.class);
			cancel.setAction("cancel");
			cancelIntent = PendingIntent.getService(this, startId, cancel, 0);

			builder.setPriority(Notification.PRIORITY_DEFAULT)
					.setLights(0, 0, 0)
					.setVibrate(null)
					.setSound(null);
		}

		builder.addAction(new Notification.Action.Builder(
				null, getResources().getString(R.string.cancel), cancelIntent
		).build());
		Notification notification = builder.build();
		startForeground(startId, notification);

		root = DocumentFile.fromTreeUri(this, intent.getData());
		try {
			listener = new ServerSocket(TransferApp.TCP_PORT);
			Log.d(LOG_TAG, "ReceiveService begins to listen");
		} catch (IOException e) {
			Log.e(LOG_TAG, "listener init error", e);
		}

		thread = new Thread(this);
		thread.start();

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void run() {
		try {
			Socket socket = listener.accept();
			InputStream in = socket.getInputStream();
			Pipe pipe = new Pipe(64);
			DirectoryWriter writer = new DirectoryWriter(
					getContentResolver(),
					root, pipe);
			Thread writerThread = new Thread(writer);
			writerThread.start();
			try {
				while (true) {
					byte[] buffer = new byte[1024 * 1024];
					int read = in.read(buffer);
					if (read == buffer.length)
						pipe.write(buffer);
					else if (read > 0) {
						byte[] data = new byte[read];
						System.arraycopy(buffer, 0, data, 0, read);
						pipe.write(data);
					} else break;
				}
				pipe.close();
				in.close();
				socket.close();
			} catch (InterruptedException ignored) {
				writerThread.interrupt();
			} catch (IOException e) {
				Log.e(LOG_TAG, "TransferService", e);
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, "listener accept error", e);
		}

	}

	@Override
	public void onDestroy() {
		if (listener != null) {
			try {
				listener.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, "listener close error", e);
			}
			listener = null;
		}
		Log.d(LOG_TAG, "ReceiveService closed");
		super.onDestroy();
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException();
	}
}
