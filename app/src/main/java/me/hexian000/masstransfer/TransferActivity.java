package me.hexian000.masstransfer;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

public class TransferActivity extends Activity {
	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_transfer);
	}
}
