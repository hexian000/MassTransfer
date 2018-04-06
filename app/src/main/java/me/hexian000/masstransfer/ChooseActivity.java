package me.hexian000.masstransfer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

public class ChooseActivity extends Activity {
	List<DocumentFile> files;
	int dirCount;
	private Handler handler = new Handler();
	private ListView listView;
	private ProgressBar progressBar;

	@Override
	protected void onDestroy() {
		handler = null;
		super.onDestroy();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.choose_activity);

		listView = findViewById(R.id.List);
		progressBar = findViewById(R.id.ListLoading);

		files = new ArrayList<>();
		ArrayAdapter adapter = new ArrayAdapter<DocumentFile>(this,
				R.layout.choose_file_item, files) {
			@NonNull
			@Override
			public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
				if (convertView == null) {
					convertView = getLayoutInflater().inflate(R.layout.choose_file_item, parent, false);
					ItemViewHolder holder = new ItemViewHolder(convertView);
					if (position < dirCount)
						holder.imageView.setImageResource(R.drawable.ic_folder_white_24dp);
					else
						holder.imageView.setImageResource(R.drawable.ic_file_white_24dp);
					holder.textView.setText(((DocumentFile) listView.getItemAtPosition(position)).getName());
					convertView.setTag(holder);
				}
				return convertView;
			}

			@Override
			public boolean hasStableIds() {
				return true;
			}
		};
		listView.setAdapter(adapter);

		final Uri rootUri = getIntent().getData();
		new Thread(() -> {
			long start = System.currentTimeMillis();
			DocumentFile root = DocumentFile.fromTreeUri(this, rootUri);
			if (root == null || !root.isDirectory()) {
				if (handler != null)
					handler.post(this::finish);
				return;
			}
			Log.v(LOG_TAG, "time used: " + (System.currentTimeMillis() - start));
			DocumentFile[] list = root.listFiles();
			SortedMap<String, DocumentFile> dirList = new TreeMap<>();
			SortedMap<String, DocumentFile> fileList = new TreeMap<>();
			for (DocumentFile file : list) {
				String name = file.getName();
				if (name.startsWith("."))
					continue;
				if (file.isFile() && file.canRead()) {
					fileList.put(name.toLowerCase(), file);
				} else if (file.isDirectory()) {
					dirList.put(name.toLowerCase(), file);
				}
			}
			Log.v(LOG_TAG, "time used: " + (System.currentTimeMillis() - start));
			files.addAll(dirList.values());
			files.addAll(fileList.values());
			dirCount = dirList.size();
			Log.v(LOG_TAG, "time used: " + (System.currentTimeMillis() - start));
			if (handler != null)
				handler.post(() -> {
					progressBar.setVisibility(View.INVISIBLE);
					adapter.notifyDataSetChanged();
				});
			Log.v(LOG_TAG, "time used: " + (System.currentTimeMillis() - start));
		}).start();
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
					DocumentFile file = ((DocumentFile) listView.getItemAtPosition(positions.keyAt(i)));
					chosenFiles[i] = file.getName();
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
