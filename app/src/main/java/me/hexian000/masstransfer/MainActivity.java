package me.hexian000.masstransfer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
	private static final int REQUEST_SEND = 1;
	private static final int REQUEST_RECEIVE = 2;
	private static final int REQUEST_CHOOSE = 3;
	final Handler handler = new Handler(Looper.myLooper());
	private Uri uri;
	private Button sendButton, receiveButton;

	@Override
	protected void onResume() {
		super.onResume();
		updateButtons();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent result) {
		if (resultCode != RESULT_OK) {
			return;
		}
		switch (requestCode) {
		case REQUEST_SEND: {
			uri = result.getData();
			if (uri != null) {
				Intent intent = new Intent(this, ChooseActivity.class);
				intent.setData(uri);
				startActivityForResult(intent, REQUEST_CHOOSE);
			}
		}
		break;
		case REQUEST_RECEIVE: {
			uri = result.getData();
			if (uri != null) {
				Intent intent = new Intent(this, ReceiveService.class);
				intent.setData(uri);
				startForegroundServiceCompat(intent);
				Toast.makeText(MainActivity.this, R.string.start_receive_service, Toast.LENGTH_SHORT).show();
				receiveButton.setText(R.string.receive_cancel_button);
			}
		}
		break;
		case REQUEST_CHOOSE: {
			String[] fileList = result.getStringArrayExtra("files");
			if (fileList == null) {
				break;
			}
			Intent intent = new Intent(this, PeerListActivity.class);
			intent.setData(uri);
			intent.putExtra("files", fileList);
			startActivity(intent);
		}
		break;
		}
	}

	void updateButtons() {
		if (((MassTransfer) getApplicationContext()).receiveService == null) {
			receiveButton.setText(R.string.receive_button);
		} else {
			receiveButton.setText(R.string.receive_cancel_button);
		}
		if (((MassTransfer) getApplicationContext()).sendService == null) {
			sendButton.setText(R.string.send_button);
		} else {
			sendButton.setText(R.string.send_cancel_button);
		}
	}

	@Override
	protected void onDestroy() {
		((MassTransfer) getApplicationContext()).mainActivity = null;
		super.onDestroy();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		final MassTransfer app = (MassTransfer) getApplicationContext();
		app.mainActivity = this;

		receiveButton = findViewById(R.id.ReceiveButton);
		receiveButton.setOnClickListener((View v) -> {
			if (app.receiveService != null) {
				Intent intent = new Intent(this, ReceiveService.class);
				intent.setAction("cancel");
				startForegroundServiceCompat(intent);
			} else {
				Toast.makeText(MainActivity.this, R.string.choose_storage_directory, Toast.LENGTH_SHORT).show();
				Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
				startActivityForResult(intent, REQUEST_RECEIVE);
			}
		});

		sendButton = findViewById(R.id.SendButton);
		sendButton.setOnClickListener((View v) -> {
			Toast.makeText(MainActivity.this, R.string.choose_send_directory, Toast.LENGTH_SHORT).show();
			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
			startActivityForResult(intent, REQUEST_SEND);
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
