package me.hexian000.masstransfer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import static me.hexian000.masstransfer.MassTransfer.LOG_TAG;

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
	}

	@Override
	public void onCreate() {
		discoverer = new Discoverer(MassTransfer.UDP_PORT);
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		if (discoverer != null) {
			Log.d(LOG_TAG, "discover start");
			discoverer.start();
		}
		return mBinder;
	}

	class Binder extends android.os.Binder {
		DiscoverService getService() {
			return DiscoverService.this;
		}
	}
}
