package me.hexian000.masstransfer;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;

public class TransferService extends Service {
	private final IBinder mBinder = new TransferServiceBinder();
	ServerSocket listener = null;
	Discoverer discoverer = null;

	@Override
	public void onDestroy() {
		if (discoverer != null) {
			Log.d(TransferApp.LOG_TAG, "discover close");
			discoverer.close();
			discoverer = null;
		}
		if (listener != null) {
			try {
				listener.close();
			} catch (IOException e) {
				Log.e(TransferApp.LOG_TAG, "listener close error", e);
			}
			listener = null;
		}
		super.onDestroy();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		discoverer = new Discoverer(14644);
		try {
			listener = new ServerSocket(14645);
		} catch (IOException e) {
			Log.e(TransferApp.LOG_TAG, "listener init error", e);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TransferApp.LOG_TAG, "discover start");
		discoverer.start();
		return super.onStartCommand(intent, flags, startId);
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	public class TransferServiceBinder extends Binder {
		TransferService getService() {
			return TransferService.this;
		}
	}
}
