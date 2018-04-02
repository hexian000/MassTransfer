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
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;

import static me.hexian000.masstransfer.TransferApp.CHANNEL_TRANSFER_STATE;
import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

public class ReceiveService extends Service {
	ServerSocket listener = null;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ("cancel".equals(intent.getAction())) {
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


		try {
			listener = new ServerSocket(TransferApp.TCP_PORT);
			Log.d(LOG_TAG, "ReceiveService begins to listen");
		} catch (IOException e) {
			Log.e(LOG_TAG, "listener init error", e);
		}
		return super.onStartCommand(intent, flags, startId);
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
