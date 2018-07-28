package me.hexian000.masstransfer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.support.annotation.CallSuper;
import android.support.annotation.StringRes;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import me.hexian000.masstransfer.io.ProgressReporter;

import static me.hexian000.masstransfer.MassTransfer.*;

public abstract class TransferService extends Service {
	final static int TcpBufferSize = 512 * 1024;
	private static PowerManager.WakeLock wakeLock = null;
	private static WifiManager.WifiLock wifiLock = null;
	final Handler handler = new Handler();
	DocumentFile root = null;
	Notification.Builder builder;
	NotificationManager notificationManager = null;
	Thread thread = null;
	int startId = 0;
	boolean result = false;

	void acquireLocks() {
		releaseLocks();
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		if (powerManager != null) {
			wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOG_TAG);
			wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);
			Log.d(LOG_TAG, "WakeLock acquired for 10 minutes");
		}
		WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		if (wifiManager != null) {
			wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, LOG_TAG);
			wifiLock.acquire();
			Log.d(LOG_TAG, "WifiLock acquired");
		}
	}

	void releaseLocks() {
		if (wakeLock != null) {
			if (wakeLock.isHeld()) {
				wakeLock.release();
				Log.d(LOG_TAG, "WakeLock released");
			}
			wakeLock = null;
		}
		if (wifiLock != null) {
			if (wifiLock.isHeld()) {
				wifiLock.release();
				Log.d(LOG_TAG, "WifiLock released");
			}
			wifiLock = null;
		}
	}

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
				.setOnlyAlertOnce(true)
				.setVisibility(Notification.VISIBILITY_PUBLIC);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// Android 8.0+
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (manager != null) {
				MassTransfer.createNotificationChannels(manager, getResources());
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

	@CallSuper
	@Override
	public void onDestroy() {
		notificationManager = null;
		postUpdateButton();
		releaseLocks();
		super.onDestroy();
	}

	void postUpdateButton() {
		MainActivity mainActivity = ((MassTransfer) getApplicationContext()).mainActivity;
		if (mainActivity != null) {
			mainActivity.handler.post(mainActivity::updateButtons);
		}
	}

	void stop() {
		if (thread != null) {
			thread.interrupt();
			thread = null;
		}
		builder = null;
		releaseLocks();
		stopSelf();
	}

	void showResult() {
		Notification.Builder builder = new Notification.Builder(this.getApplicationContext());

		if (result) {
			builder.setContentTitle(getResources().getString(R.string.transfer_success))
					.setSmallIcon(R.drawable.ic_done_black_24dp);
		} else {
			builder.setContentTitle(getResources().getString(R.string.transfer_failed))
					.setSmallIcon(R.drawable.ic_error_outline_black_24dp);
		}

		builder.setContentIntent(null)
				.setWhen(System.currentTimeMillis())
				.setOngoing(false)
				.setVisibility(Notification.VISIBILITY_PUBLIC);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// Android 8.0+
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (manager != null) {
				builder.setChannelId(CHANNEL_TRANSFER_RESULT);
			}
		} else {
			// Android 7.1
			builder.setPriority(Notification.PRIORITY_DEFAULT);
		}

		notificationManager.notify(startId, builder.build());
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
