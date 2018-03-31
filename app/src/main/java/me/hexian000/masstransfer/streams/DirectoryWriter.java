package me.hexian000.masstransfer.streams;

/*
 * DirectoryWriter 是对系统自带的 FileOutputStream 的封装
 * TODO 基于系统接口，实现文件夹的流化
 */

import android.content.ContentResolver;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

class DirectoryWriter implements Runnable {
	ContentResolver resolver;
	DocumentFile root;
	Reader in;

	DirectoryWriter(ContentResolver resolver,
	                DocumentFile root,
	                Reader in) {
		this.resolver = resolver;
		this.root = root;
		this.in = in;
	}

	private DocumentFile makePath(String[] segments) {
		DocumentFile parent = root;
		for (String name : segments) {
			DocumentFile current = null;
			for (DocumentFile file : parent.listFiles()) {
				if (name.equals(file.getName())) {
					current = file;
					break;
				}
			}
			if (current == null) {
				current = parent.createDirectory(name);
			}
			parent = current;
		}
		return parent;
	}

	private void writeFile(String path, long length) {
		if (length == 0) { // is directory
			makePath(path.split("/"));
			return;
		}
		// is File
		String[] pathSegments = path.split("/");
		if (pathSegments.length > 1) {
			String[] parentSegments = new String[pathSegments.length - 1];
			System.arraycopy(pathSegments, 0,
					parentSegments, 0,
					pathSegments.length - 1);
			makePath(parentSegments);
		}
		// TODO writeFile
	}

	@Override
	public void run() {
		try {
			do {
				int read;
				ByteBuffer lengths = ByteBuffer.allocate(Integer.BYTES + Long.BYTES).
						order(ByteOrder.BIG_ENDIAN);
				read = in.read(lengths.array());
				if (read != Integer.BYTES + Long.BYTES) {
					Log.e(LOG_TAG, "invalid header");
					return;
				}
				int nameLen = lengths.getInt();
				long fileLen = lengths.getLong();
				if (nameLen == 0 && fileLen == 0) {
					return; // bye
				}
				byte[] name = new byte[nameLen];
				read = in.read(name);
				if (read != nameLen) {
					Log.e(LOG_TAG, "can't read name");
					return;
				}
				String path = new String(name, "UTF-8");
				writeFile(path, fileLen);
			} while (true);
		} catch (IOException | InterruptedException e) {
			Log.e(LOG_TAG, "DirectoryWriter", e);
		}
	}
}
