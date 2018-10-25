package me.hexian000.masstransfer;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.res.Resources;
import android.os.Build;

import java.text.DecimalFormat;

public class MassTransfer extends Application {
	public final static String LOG_TAG = "MassTransfer";
	public final static String CHANNEL_TRANSFER_STATE = "transfer_state";
	public final static String CHANNEL_TRANSFER_RESULT = "transfer_result";
	public final static int UDP_PORT = 14644;
	public final static int TCP_PORT = 14645;
	public final static int IPTOS_THROUGHPUT = 0x08;
	private final static DecimalFormat prettyFormat = new DecimalFormat("#.##");
	public MainActivity mainActivity = null;
	public ReceiveService receiveService = null;
	public SendService sendService = null;

	static void createNotificationChannels(NotificationManager manager, Resources res) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			{
				NotificationChannel channel = new NotificationChannel(CHANNEL_TRANSFER_STATE,
						res.getString(R.string.channel_transfer_state), NotificationManager.IMPORTANCE_DEFAULT);
				channel.enableLights(false);
				channel.enableVibration(false);
				channel.setSound(null, null);

				manager.createNotificationChannel(channel);
			}
			{
				NotificationChannel channel = new NotificationChannel(CHANNEL_TRANSFER_RESULT,
						res.getString(R.string.channel_transfer_result), NotificationManager.IMPORTANCE_DEFAULT);
				channel.setSound(null, null);

				manager.createNotificationChannel(channel);
			}
		}
	}

	public static String formatSize(double size) {
		if (size < 2.0 * 1024.0) { // Byte
			return prettyFormat.format(size) + "B";
		} else if (size < 2.0 * 1024.0 * 1024.0) { // KB
			return prettyFormat.format(size / 1024.0) + "KB";
		} else if (size < 2.0 * 1024.0 * 1024.0 * 1024.0) { // MB
			return prettyFormat.format(size / 1024.0 / 1024.0) + "MB";
		} else { // GB
			return prettyFormat.format(size / 1024.0 / 1024.0 / 1024.0) + "GB";
		}
	}
}
