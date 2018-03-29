package me.hexian000.masstransfer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

public class DiscoverService extends Service {
	private final IBinder mBinder = new Binder();
	Discoverer discoverer = null;

	@Override
	public void onDestroy() {
		if (discoverer != null) {
			Log.d(TransferApp.LOG_TAG, "discover close");
			discoverer.close();
			discoverer = null;
		}
		super.onDestroy();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		discoverer = new Discoverer(TransferApp.UDP_PORT);
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

	public class Binder extends android.os.Binder {
		DiscoverService getService() {
			return DiscoverService.this;
		}
	}
}
