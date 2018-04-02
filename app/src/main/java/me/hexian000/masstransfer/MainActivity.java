package me.hexian000.masstransfer;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

public class MainActivity extends Activity {
	private static final int REQUEST_OPEN_DOCUMENT_TREE = 421;
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
		super.onPause();
		unbindService(mConnection);
		Log.d(LOG_TAG, "stop DiscoverService");
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		Intent intent1 = new Intent(this, DiscoverService.class);
		bindService(intent1, mConnection, Context.BIND_AUTO_CREATE);
		Log.d(LOG_TAG, "start DiscoverService");

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
							if (isServiceRunning(TransferService.class)) {
								Toast.makeText(MainActivity.this,
										R.string.transfer_service_is_already_running,
										Toast.LENGTH_SHORT).show();
								return;
							}

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
	}

	private void pickFolder() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		startActivityForResult(intent, REQUEST_OPEN_DOCUMENT_TREE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK && requestCode == REQUEST_OPEN_DOCUMENT_TREE) {
			Uri uriTree = data.getData();
			if (uriTree != null) {
				Intent intent = new Intent(this, TransferService.class);
				intent.setData(uriTree);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					startForegroundService(intent);
				} else {
					startService(intent);
				}

				finish();
			}
		}
	}

	private void listTree(DocumentFile root, List<DocumentFile> files) {
		if (files.size() > 20) return;
		for (DocumentFile file : root.listFiles()) {
			if (file.isDirectory()) {
				listTree(file, files);
			} else if (file.canRead()) {
				files.add(file);
			}
		}
	}

	private boolean isServiceRunning(Class<?> serviceClass) {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		if (manager != null) {
			for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
				if (serviceClass.getName().equals(service.service.getClassName())) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		final Button receiveButton = findViewById(R.id.ReceiveButton);
		receiveButton.setOnClickListener((View v) -> {
			if (isServiceRunning(ReceiveService.class)) {
				Toast.makeText(MainActivity.this, R.string.receive_service_is_already_running, Toast.LENGTH_SHORT).show();
				return;
			}

			Intent intent = new Intent(this, ReceiveService.class);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				startForegroundService(intent);
			} else {
				startService(intent);
			}
			Toast.makeText(MainActivity.this, R.string.start_receive_service, Toast.LENGTH_SHORT).show();
			finish();
		});
	}
}
