package me.hexian000.masstransfer;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

public class TransferApp extends Application {
	public final static String LOG_TAG = "MassTransfer";
	public final static String CHANNEL_TRANSFER_STATE = "transfer_state";
	public final static int UDP_PORT = 14644;
	public final static int TCP_PORT = 14645;

	public MainActivity mainActivity = null;
	public ReceiveService receiveService = null;
	public TransferService transferService = null;
	public DiscoverService discoverService = null;

	static void createNotificationChannels(NotificationManager manager, Resources res) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(CHANNEL_TRANSFER_STATE, res.getString(R.string
					.channel_transfer_state), NotificationManager.IMPORTANCE_DEFAULT);
			channel.enableLights(false);
			channel.enableVibration(false);
			channel.setSound(null, null);

			manager.createNotificationChannel(channel);
		}
	}

	public static String sizeToString(double size) {
		if (size < 2.0 * 1024.0) { // Byte
			return String.format(Locale.getDefault(), "%.0fB", size);
		} else if (size < 2.0 * 1024.0 * 1024.0) { // KB
			return String.format(Locale.getDefault(), "%.2fKB", size / 1024.0);
		} else if (size < 2.0 * 1024.0 * 1024.0 * 1024.0) { // MB
			return String.format(Locale.getDefault(), "%.2fMB", size / 1024.0 / 1024.0);
		} else { // GB
			return String.format(Locale.getDefault(), "%.2fGB", size / 1024.0 / 1024.0 / 1024.0);
		}
	}
}
