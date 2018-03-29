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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {
	private static final int READ_REQUEST_CODE = 42;
	DiscoverService mService;
	Timer timer;
	Handler refresh = new Handler();
	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			DiscoverService.Binder binder =
					(DiscoverService.Binder) service;
			mService = binder.getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mService = null;
		}
	};

	@Override
	protected void onPause() {
		Intent intent1 = new Intent(MainActivity.this, DiscoverService.class);
		unbindService(mConnection);
		stopService(intent1);
		Log.d(TransferApp.LOG_TAG, "stop DiscoverService");
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		Intent intent1 = new Intent(MainActivity.this, DiscoverService.class);
		bindService(intent1, mConnection, Context.BIND_AUTO_CREATE);
		startService(intent1);
		Log.d(TransferApp.LOG_TAG, "start DiscoverService");

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
						peersList.setOnItemClickListener((adapterView, view, i, l) -> {
							String s = (String) adapter.getItem(i);
							Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show();
							pickFolder();
						});
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

	private void pickFolder() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		startActivityForResult(intent, READ_REQUEST_CODE);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}
}
