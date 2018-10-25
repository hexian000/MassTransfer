package me.hexian000.masstransfer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
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

import static me.hexian000.masstransfer.MassTransfer.LOG_TAG;

public class PeerListActivity extends Activity {
	private final Handler handler = new Handler();
	private List<String> items;
	private ArrayAdapter adapter;
	private Timer timer;
	private DiscoverService discoverService;
	private final ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			DiscoverService.Binder binder = (DiscoverService.Binder) service;
			discoverService = binder.getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			discoverService = null;
		}
	};

	@Override
	protected void onResume() {
		super.onResume();
		Intent intent1 = new Intent(this, DiscoverService.class);
		bindService(intent1, serviceConnection, Context.BIND_AUTO_CREATE);
		Log.d(LOG_TAG, "bind DiscoverService in MainActivity");

		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (adapter != null) {
					handler.post(() -> {
						items.clear();
						if (discoverService != null && discoverService.discoverer != null) {
							items.addAll(discoverService.discoverer.getPeers());
						}
						adapter.notifyDataSetChanged();
					});
				}
			}
		}, 0, 200);
	}

	@Override
	protected void onPause() {
		unbindService(serviceConnection);
		Log.d(LOG_TAG, "unbind DiscoverService in MainActivity");
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
		super.onPause();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_peer_list);

		items = new ArrayList<>();
		ListView peersList = findViewById(R.id.PeerList);
		adapter = new ArrayAdapter<>(PeerListActivity.this, android.R.layout.simple_list_item_1, items);
		peersList.setAdapter(adapter);
		peersList.setOnItemClickListener((adapterView, view, i, l) -> {
			if (((MassTransfer) getApplicationContext()).sendService != null) {
				Toast.makeText(PeerListActivity.this, R.string.transfer_service_is_already_running,
						Toast.LENGTH_SHORT).show();
				return;
			}

			Intent startIntent = getIntent();
			Intent intent = new Intent(this, SendService.class);
			intent.putExtra("host", (String) adapter.getItem(i));
			if (Intent.ACTION_SEND.equals(startIntent.getAction()) ||
					Intent.ACTION_SEND_MULTIPLE.equals(startIntent.getAction())) {
				intent.setAction(startIntent.getAction());
				intent.setData(startIntent.getParcelableExtra(Intent.EXTRA_STREAM));
			} else {
				intent.setData(startIntent.getData());
				intent.putExtra("files", startIntent.getStringArrayExtra("files"));
			}
			startForegroundServiceCompat(intent);
			finish();
		});
	}

	private void startForegroundServiceCompat(Intent intent) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForegroundService(intent);
		} else {
			startService(intent);
		}
	}
}
