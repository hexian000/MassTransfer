package me.hexian000.masstransfer;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.RelativeLayout;

public class ItemLayout extends RelativeLayout implements Checkable {
	private CheckBox checkBox = null;

	public ItemLayout(Context context) {
		super(context);
	}

	@Override
	public boolean isChecked() {
		if (checkBox == null) {
			checkBox = findViewById(R.id.checkBox);
		}
		return checkBox.isChecked();
	}

	@Override
	public void setChecked(boolean checked) {
		if (checkBox == null) {
			checkBox = findViewById(R.id.checkBox);
		}
		checkBox.setChecked(checked);
	}

	@Override
	public void toggle() {
		if (checkBox == null) {
			checkBox = findViewById(R.id.checkBox);
		}
		checkBox.setChecked(!checkBox.isChecked());
	}
}
