package me.hexian000.masstransfer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

public class DiscoverService extends Service {
	private final IBinder mBinder = new Binder();
	Discoverer discoverer = null;

	@Override
	public void onDestroy() {
		if (discoverer != null) {
			Log.d(LOG_TAG, "discover close");
			discoverer.close();
			discoverer = null;
		}
		((TransferApp) getApplicationContext()).discoverService = null;
	}

	@Override
	public void onCreate() {
		((TransferApp) getApplicationContext()).discoverService = this;
		discoverer = new Discoverer(TransferApp.UDP_PORT);
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		Log.d(LOG_TAG, "discover start");
		discoverer.start();
		return mBinder;
	}

	class Binder extends android.os.Binder {
		DiscoverService getService() {
			return DiscoverService.this;
		}
	}
}
