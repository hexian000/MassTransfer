package me.hexian000.masstransfer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
	DiscoverService mService;
	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			DiscoverService.Binder binder =
					(DiscoverService.Binder) service;
			mService = binder.getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mService = null;
		}
	};

	private void stop() {
		if (mService != null) {
			unbindService(mConnection);
			Log.d(LOG_TAG, "unbind DiscoverService in ReceiveService");
		}
		stopSelf();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ("cancel".equals(intent.getAction())) {
			if (thread != null) {
				thread.interrupt();
				thread = null;
			}
			stop();
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

		thread = new Thread(this);
		thread.start();

		Intent discover = new Intent(this, DiscoverService.class);
		bindService(discover, mConnection, Context.BIND_AUTO_CREATE);
		Log.d(LOG_TAG, "bind DiscoverService in ReceiveService");

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void run() {
		try {
			listener = new ServerSocket(TransferApp.TCP_PORT);
			Log.d(LOG_TAG, "ReceiveService begins to listen");
		} catch (IOException e) {
			Log.e(LOG_TAG, "listener init error", e);
			stop();
			return;
		}
		Socket socket;
		InputStream in;
		try {
			socket = listener.accept();
			socket.setReceiveBufferSize(16 * 1024 * 1024);
			socket.setSoLinger(true, 10);
			socket.setSoTimeout(10 * 1000);
			in = socket.getInputStream();
		} catch (IOException e) {
			Log.e(LOG_TAG, "listener accept error", e);
			stop();
			return;
		}
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
			Log.d(LOG_TAG, "ReceiveService finished normally");
		} catch (InterruptedException ignored) {
			Log.d(LOG_TAG, "ReceiveService interrupted");
			writerThread.interrupt();
		} catch (IOException e) {
			Log.e(LOG_TAG, "ReceiveService", e);
		}
		stop();
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
