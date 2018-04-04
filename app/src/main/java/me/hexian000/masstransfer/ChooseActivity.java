package me.hexian000.masstransfer;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

public class ChooseActivity extends ListActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.choose_activity);

		List<DocumentFile> files;
		int dirCount;
		{
			Intent intent = getIntent();
			DocumentFile root = DocumentFile.fromTreeUri(this, intent.getData());
			if (root == null || !root.isDirectory()) {
				finish();
				return;
			}
			DocumentFile[] list = root.listFiles();
			List<DocumentFile> dirList = new ArrayList<>();
			List<DocumentFile> fileList = new ArrayList<>();
			files = new ArrayList<>();
			for (DocumentFile file : list) {
				if (file.getName().startsWith("."))
					continue;
				if (file.isFile() && file.canRead()) {
					fileList.add(file);
				} else if (file.isDirectory()) {
					dirList.add(file);
				}
			}
			dirList.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
			fileList.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
			files.addAll(dirList);
			files.addAll(fileList);
			dirCount = dirList.size();
		}

		final ListView listView = getListView();
		ArrayAdapter adapter = new ArrayAdapter<DocumentFile>(this,
				R.layout.choose_file_item, files) {
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
				if (position < dirCount)
					holder.imageView.setImageResource(R.drawable.ic_folder_white_24dp);
				else
					holder.imageView.setImageResource(R.drawable.ic_file_white_24dp);
				holder.textView.setText(((DocumentFile) listView.getItemAtPosition(position)).getName());
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
				SparseBooleanArray positions = getListView().getCheckedItemPositions();
				String[] chosenFiles = new String[positions.size()];
				for (int i = 0; i < positions.size(); i++) {
					DocumentFile file = ((DocumentFile) getListView().getItemAtPosition(positions.keyAt(i)));
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
