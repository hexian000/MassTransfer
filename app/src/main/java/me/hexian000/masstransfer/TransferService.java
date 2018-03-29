package me.hexian000.masstransfer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;

public class TransferService extends Service {
	private final IBinder mBinder = new Binder();
	ServerSocket listener = null;

	@Override
	public void onCreate() {
		try {
			listener = new ServerSocket(TransferApp.TCP_PORT);
		} catch (IOException e) {
			Log.e(TransferApp.LOG_TAG, "listener init error", e);
		}
		super.onCreate();
	}

	@Override
	public void onDestroy() {
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

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	public class Binder extends android.os.Binder {
		TransferService getService() {
			return TransferService.this;
		}
	}
}
