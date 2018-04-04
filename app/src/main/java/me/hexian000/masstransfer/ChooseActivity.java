package me.hexian000.masstransfer;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.provider.DocumentFile;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

public class ChooseActivity extends ListActivity {
	private List<String> files;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.choose_activity);

		Intent intent = getIntent();
		DocumentFile root = DocumentFile.fromTreeUri(this, intent.getData());
		if (root == null || !root.isDirectory()) {
			finish();
			return;
		}

		DocumentFile[] fileList = root.listFiles();
		List<String> fileNames = new ArrayList<>();
		files = new ArrayList<>();
		for (DocumentFile file : fileList) {
			if (file.isFile() && file.canRead()) {
				files.add(file.getUri().toString());
				fileNames.add(file.getName());
			} else if (file.isDirectory()) {
				files.add(file.getUri().toString());
				fileNames.add(file.getName() + "/...");
			}
		}

		ArrayAdapter adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice,
				fileNames);
		getListView().setAdapter(adapter);
		getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.choose_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.ok: {
				List<String> chosenFiles = new ArrayList<>();
				Intent intent = new Intent();
				SparseBooleanArray positions = getListView().getCheckedItemPositions();
				for (int i = 0; i < positions.size(); i++) {
					chosenFiles.add(files.get(positions.keyAt(i)));
				}
				intent.putExtra("files", chosenFiles.toArray());
				setResult(RESULT_OK, intent);
				finish();
			}
			break;
		}
		return super.onOptionsItemSelected(item);
	}
}
