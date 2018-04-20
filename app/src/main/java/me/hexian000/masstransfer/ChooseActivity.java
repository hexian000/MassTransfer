package me.hexian000.masstransfer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

public class ChooseActivity extends Activity {
	int dirCount;
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
				if (name.startsWith(".")) {
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
			String[] chosenFiles = new String[positions.size()];
			for (int i = 0; i < positions.size(); i++) {
				String name = ((String) listView.getItemAtPosition(positions.keyAt(i)));
				chosenFiles[i] = name;
			}
			intent.putExtra("files", chosenFiles);
			setResult(RESULT_OK, intent);
			finish();
		}
		break;
		}
		return super.onOptionsItemSelected(item);
	}

	private class ItemViewHolder {
		ImageView imageView;
		TextView textView;
		CheckBox checkBox;

		ItemViewHolder(View itemView) {
			imageView = itemView.findViewById(R.id.imageView);
			textView = itemView.findViewById(R.id.textView);
			checkBox = itemView.findViewById(R.id.checkBox);
		}
	}
}
