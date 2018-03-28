package me.hexian000.masstransfer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {
	TransferService mService;
	Timer timer;
	Handler refresh = new Handler();
	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			TransferService.TransferServiceBinder binder =
					(TransferService.TransferServiceBinder) service;
			mService = binder.getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mService = null;
		}
	};

	@Override
	protected void onPause() {
		Intent intent1 = new Intent(MainActivity.this, TransferService.class);
		unbindService(mConnection);
		stopService(intent1);
		Log.d(TransferApp.LOG_TAG, "stop TransferService");
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		Intent intent1 = new Intent(MainActivity.this, TransferService.class);
		bindService(intent1, mConnection, Context.BIND_AUTO_CREATE);
		startService(intent1);
		Log.d(TransferApp.LOG_TAG, "start TransferService");

		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			List<String> items = new ArrayList<>();
			ArrayAdapter adapter = null;

			@Override
			public void run() {
				if (adapter == null) {
					refresh.post(() -> {
						ListView peersList = findViewById(R.id.PeersList);
						adapter = new ArrayAdapter<>(
								MainActivity.this,
								android.R.layout.simple_list_item_1,
								items);
						peersList.setAdapter(adapter);
					});
				} else {
					refresh.post(() -> {
						items.clear();
						if (mService != null)
							items.addAll(mService.discoverer.getPeers());
						adapter.notifyDataSetChanged();
					});
				}
			}
		}, 0, 500);

		super.onResume();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}
}
