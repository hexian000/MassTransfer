package me.hexian000.masstransfer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.widget.Toast;
import me.hexian000.masstransfer.io.DirectoryReader;
import me.hexian000.masstransfer.io.Pipe;
import me.hexian000.masstransfer.io.RateCounter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

import static me.hexian000.masstransfer.TransferApp.*;


public class TransferService extends Service implements Runnable {
	Notification.Builder builder;
	int startId = 0;
	String host;
	Thread thread = null;
	DocumentFile root = null;
	String[] files = null;
	NotificationManager notificationManager = null;
	boolean result;

	private void initNotification() {
		if (builder == null) {
			builder = new Notification.Builder(this.getApplicationContext());
		}
		builder.setContentIntent(null).setContentTitle(getResources().getString(R.string.notification_sending))
				.setSmallIcon(R.drawable.ic_send_black_24dp).setWhen(System.currentTimeMillis()).setProgress(0, 0,
				true).setOngoing(true).setVisibility(Notification.VISIBILITY_PUBLIC);

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
		Intent cancel = new Intent(this, TransferService.class);
		cancel.setAction("cancel");
		builder.addAction(new Notification.Action.Builder(null, getResources().getString(R.string.cancel),
				PendingIntent.getService(this, startId, cancel, 0)).build()).setContentText(getResources().getString(R
				.string.notification_starting));
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ("cancel".equals(intent.getAction())) {
			Log.d(LOG_TAG, "TransferService user cancelled");
			if (thread != null) {
				Log.d(LOG_TAG, "try interrupt transfer thread");
				thread.interrupt();
				thread = null;
			}
			notificationManager = null;
			builder = null;
			stopSelf();
			return START_NOT_STICKY;
		}

		this.startId = startId;
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		initNotification();
		Notification notification = builder.build();
		startForeground(startId, notification);

		Bundle extras = intent.getExtras();
		if (extras == null) {
			stopSelf();
			return START_NOT_STICKY;
		}
		host = extras.getString("host");
		String[] uriStrings = extras.getStringArray("files");
		if (host == null || uriStrings == null) {
			stopSelf();
			return START_NOT_STICKY;
		}
		root = DocumentFile.fromTreeUri(this, intent.getData());
		files = extras.getStringArray("files");

		thread = new Thread(this);
		thread.start();
		return START_NOT_STICKY;
	}

	@Override
	public void run() {
		try (Socket socket = new Socket()) {
			socket.setPerformancePreferences(0, 0, 1);
			socket.setSendBufferSize(8 * 1024 * 1024);
			socket.setSoTimeout(4000);
			socket.setSoLinger(true, 10);
			socket.connect(new InetSocketAddress(InetAddress.getByName(host), TCP_PORT), 4000);
			runPipe(socket);
		} catch (IOException e) {
			Log.e(LOG_TAG, "connect failed", e);
		} finally {
			thread = null;
			notificationManager = null;
			builder = null;
			stopSelf();
		}
	}

	private void runPipe(Socket socket) {
		final int pipeSize = 16 * 1024 * 1024;
		Pipe pipe = new Pipe(pipeSize);
		DirectoryReader reader = new DirectoryReader(getContentResolver(), root, files, pipe, (text, now, max) -> {
			if (builder != null && notificationManager != null) {
				if (text != null) {
					text += "\n";
					if (pipe.getSize() > pipeSize / 2) {
						text += getResources().getString(R.string.bottleneck_network);
					} else {
						text += getResources().getString(R.string.bottleneck_local);
					}
				} else {
					text = getResources().getString(R.string.notification_finishing);
				}
				builder.setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setProgress(max,
						now, max == now && now == 0);
				notificationManager.notify(startId, builder.build());
			}
		});
		Thread readerThread = new Thread(reader);
		readerThread.start();
		Timer timer = new Timer();
		try (OutputStream out = socket.getOutputStream()) {
			RateCounter rate = new RateCounter();
			final int rateInterval = 10;
			timer.schedule(new TimerTask() {

				@Override
				public void run() {
					if (builder != null && notificationManager != null) {
						builder.setSubText(TransferApp.sizeToString(rate.rate() / rateInterval) + "/s");
						notificationManager.notify(startId, builder.build());
					}
				}
			}, rateInterval * 1000, rateInterval * 1000);
			while (thread != null) {
				byte[] buffer = new byte[1024];
				byte[] writeBuffer;
				int read = pipe.read(buffer);
				if (read == buffer.length) {
					writeBuffer = buffer;
				} else if (read > 0) {
					writeBuffer = new byte[read];
					System.arraycopy(buffer, 0, writeBuffer, 0, read);
				} else {
					break;
				}
				out.write(writeBuffer);
				rate.increase(writeBuffer.length);
			}
			readerThread.join();
			result = reader.isSuccess();
			Log.d(LOG_TAG, "TransferService finished normally");
		} catch (InterruptedException e) {
			Log.d(LOG_TAG, "TransferService interrupted");
		} catch (IOException e) {
			Log.e(LOG_TAG, "TransferService", e);
		} finally {
			timer.cancel();
			if (readerThread.isAlive()) {
				readerThread.interrupt();
			}
		}
	}

	@Override
	public void onCreate() {
		((TransferApp) getApplicationContext()).transferService = this;
		result = false;
	}

	@Override
	public void onDestroy() {
		if (result) {
			Toast.makeText(this, R.string.transfer_success, Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(this, R.string.transfer_failed, Toast.LENGTH_SHORT).show();
		}
		((TransferApp) getApplicationContext()).transferService = null;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException();
	}
}
