package me.hexian000.masstransfer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.List;

public class ChooseActivity extends Activity {
	private int dirCount;
	private Handler handler;
	private ProgressBar progressBar;
	private ListView listView;
	private ArrayAdapter adapter;
	private List<String> files;

	@Override
	protected void onResume() {
		super.onResume();
		final Uri rootUri = getIntent().getData();
		if (rootUri == null) {
			finish();
			return;
		}

		new Thread(() -> {
			DocumentFile root = DocumentFile.fromTreeUri(this, rootUri);
			if (root == null || !root.isDirectory()) {
				if (handler != null) {
					handler.post(this::finish);
				}
				return;
			}
			DocumentFile[] list = root.listFiles();
			List<String> dirList = new ArrayList<>();
			List<String> fileList = new ArrayList<>();
			for (DocumentFile file : list) {
				String name = file.getName();
				if (name == null || name.startsWith(".")) {
					continue;
				}
				if (file.isFile() && file.canRead()) {
					fileList.add(name);
				} else if (file.isDirectory()) {
					dirList.add(name);
				}
			}
			dirList.sort(String::compareToIgnoreCase);
			fileList.sort(String::compareToIgnoreCase);
			files.clear();
			files.addAll(dirList);
			files.addAll(fileList);

			dirCount = dirList.size();
			if (handler != null) {
				handler.post(() -> {
					if (adapter != null) {
						progressBar.setVisibility(View.INVISIBLE);
						adapter.notifyDataSetChanged();
					}
				});
			}
		}).start();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.choose_activity);
		handler = new Handler();
		progressBar = findViewById(R.id.ListLoading);
		listView = findViewById(R.id.List);
		files = new ArrayList<>();
		adapter = new ArrayAdapter<String>(this, R.layout.choose_file_item, files) {
			@NonNull
			@Override
			public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
				ItemViewHolder holder;
				if (convertView == null) {
					convertView = getLayoutInflater().inflate(R.layout.choose_file_item, parent, false);
					holder = new ItemViewHolder(convertView);
					convertView.setTag(holder);
				} else {
					holder = (ItemViewHolder) convertView.getTag();
				}
				if (position < dirCount) {
					holder.imageView.setImageResource(R.drawable.ic_folder_white_24dp);
				} else {
					holder.imageView.setImageResource(R.drawable.ic_file_white_24dp);
				}
				holder.textView.setText(((String) listView.getItemAtPosition(position)));
				return convertView;
			}

			@Override
			public boolean hasStableIds() {
				return true;
			}
		};
		listView.setAdapter(adapter);
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
			Intent intent = new Intent();
			intent.setData(getIntent().getData());
			SparseBooleanArray positions = listView.getCheckedItemPositions();
			List<String> chosenFiles = new ArrayList<>();
			for (int i = 0; i < positions.size(); i++) {
				if (positions.valueAt(i)) {
					chosenFiles.add((String) listView.getItemAtPosition(positions.keyAt(i)));
				}
			}
			intent.putExtra("files", chosenFiles.toArray(new String[]{}));
			setResult(RESULT_OK, intent);
			finish();
		}
		break;
		}
		return super.onOptionsItemSelected(item);
	}

	private class ItemViewHolder {
		final ImageView imageView;
		final TextView textView;

		ItemViewHolder(View itemView) {
			imageView = itemView.findViewById(R.id.imageView);
			textView = itemView.findViewById(R.id.textView);
		}
	}
}
