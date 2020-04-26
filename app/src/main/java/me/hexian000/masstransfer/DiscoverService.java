package me.hexian000.masstransfer;

import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import static me.hexian000.masstransfer.MassTransfer.LOG_TAG;

public class DiscoverService extends Service {
	private static WifiManager.MulticastLock multicastLock = null;
	private final IBinder mBinder = new Binder();
	Discoverer discoverer = null;

	void acquireLock() {
		releaseLock();
		WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		if (wifiManager != null) {
			multicastLock = wifiManager.createMulticastLock(LOG_TAG);
			multicastLock.acquire();
			Log.d(LOG_TAG, "MulticastLock acquired");
		}
	}

	void releaseLock() {
		if (multicastLock != null) {
			if (multicastLock.isHeld()) {
				multicastLock.release();
				Log.d(LOG_TAG, "MulticastLock released");
			}
			multicastLock = null;
		}
	}

	@Override
	public void onDestroy() {
		if (discoverer != null) {
			Log.d(LOG_TAG, "discover close");
			discoverer.close();
			discoverer = null;
		}
		releaseLock();
	}

	@Override
	public void onCreate() {
		acquireLock();
		discoverer = new Discoverer(MassTransfer.UDP_PORT);
		Log.d(LOG_TAG, "discover start");
		discoverer.start();
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	class Binder extends android.os.Binder {
		DiscoverService getService() {
			return DiscoverService.this;
		}
	}
}
