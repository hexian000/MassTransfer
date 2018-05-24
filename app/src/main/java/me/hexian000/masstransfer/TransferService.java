package me.hexian000.masstransfer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.StringRes;
import android.support.v4.provider.DocumentFile;
import android.widget.Toast;
import me.hexian000.masstransfer.io.ProgressReporter;

import static me.hexian000.masstransfer.TransferApp.CHANNEL_TRANSFER_STATE;

public abstract class TransferService extends Service {
	final Handler handler = new Handler();
	DocumentFile root = null;
	Notification.Builder builder;
	NotificationManager notificationManager = null;
	Thread thread = null;
	int startId = 0;
	boolean result = false;


	void initNotification(@StringRes int title) {
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		if (builder == null) {
			builder = new Notification.Builder(this.getApplicationContext());
		}

		builder.setContentIntent(null)
				.setContentTitle(getResources().getString(title))
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

		Intent cancel = new Intent(this, this.getClass());
		cancel.setAction("cancel");
		builder.addAction(new Notification.Action.Builder(null, getResources().getString(R.string.cancel),
				PendingIntent.getService(this, startId, cancel, 0)).build())
				.setContentText(getResources().getString(R.string.notification_starting));

		Notification notification = builder.build();
		startForeground(startId, notification);
	}

	void stop() {
		if (thread != null) {
			thread.interrupt();
			thread = null;
		}
		notificationManager = null;
		builder = null;
		stopSelf();
	}

	void showResultToast() {
		if (result) {
			Toast.makeText(this, R.string.transfer_success, Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(this, R.string.transfer_failed, Toast.LENGTH_SHORT).show();
		}
	}

	protected class Progress implements ProgressReporter {
		public String text;
		public long now, max;

		public synchronized Progress get() {
			Progress p = new Progress();
			p.text = text;
			p.now = now;
			p.max = max;
			return p;
		}

		@Override
		public synchronized void report(String text, long now, long max) {
			this.text = text;
			this.now = now;
			this.max = max;
		}
	}
}
