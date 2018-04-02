package me.hexian000.masstransfer;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.res.Resources;
import android.os.Build;

public class TransferApp extends Application {
	public final static String LOG_TAG = "MassTransfer";
	public final static String CHANNEL_TRANSFER_STATE = "transfer_state";
	public final static int UDP_PORT = 14644;
	public final static int TCP_PORT = 14645;

	static void createNotificationChannels(NotificationManager manager, Resources res) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(
					CHANNEL_TRANSFER_STATE, res.getString(R.string.channel_transfer_state),
					NotificationManager.IMPORTANCE_DEFAULT
			);
			channel.enableLights(false);
			channel.enableVibration(false);
			channel.setSound(null, null);

			manager.createNotificationChannel(channel);
		}
	}
}
