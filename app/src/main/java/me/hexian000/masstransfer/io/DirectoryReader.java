package me.hexian000.masstransfer.io;

/*
 * DirectoryReader 是对系统自带的 DocumentFile 的封装
 * 基于系统接口，实现文件夹的流化
 */

import android.content.ContentResolver;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

public class DirectoryReader implements Runnable {
	private ProgressReporter reporter;
	private ContentResolver resolver;
	private DocumentFile root;
	private String[] files;
	private Writer out;
	private boolean success = false;

	public DirectoryReader(ContentResolver resolver, DocumentFile root, String[] files, Writer out, ProgressReporter
			reporter) {
		this.resolver = resolver;
		this.root = root;
		this.files = files;
		this.out = out;
		this.reporter = reporter;
	}

	public boolean isSuccess() {
		return success;
	}

	private void sendDir(DocumentFile dir, String basePath) throws IOException, InterruptedException {
		if (!dir.exists()) {
			return;
		}
		String pathStr = dir.getName();
		if (pathStr.startsWith(".")) {
			return; // ignore hidden
		}
		if (basePath.length() > 0) {
			pathStr = basePath + "/" + pathStr;
		}
		byte[] path = pathStr.getBytes("UTF-8");
		ByteArrayOutputStream header = new ByteArrayOutputStream();
		ByteBuffer lengths = ByteBuffer.allocate(Integer.BYTES + Long.BYTES).order(ByteOrder.BIG_ENDIAN);
		lengths.putInt(path.length);
		Log.d(LOG_TAG, "Now at: " + pathStr);
		lengths.putLong(-1); // directory
		header.write(lengths.array());
		header.write(path);
		out.write(header.toByteArray());
		for (DocumentFile f : dir.listFiles()) {
			if (f.isFile()) {
				sendFile(f, pathStr);
			} else if (f.isDirectory()) {
				sendDir(f, pathStr);
			}
		}
	}

	private void sendFile(DocumentFile file, String basePath) throws IOException, InterruptedException {
		if (!file.exists() || !file.canRead()) {
			return;
		}
		String pathStr = file.getName();
		if (pathStr.startsWith(".")) {
			return; // ignore hidden
		}
		if (basePath.length() > 0) {
			pathStr = basePath + "/" + pathStr;
		}
		byte[] path = pathStr.getBytes("UTF-8");
		ByteArrayOutputStream header = new ByteArrayOutputStream();
		ByteBuffer lengths = ByteBuffer.allocate(Integer.BYTES + Long.BYTES).order(ByteOrder.BIG_ENDIAN);
		lengths.putInt(path.length);
		final String name = file.getName();
		Log.d(LOG_TAG, "sendFile: " + name + " length=" + file.length());
		InputStream s = resolver.openInputStream(file.getUri());
		if (s == null) {
			throw new IOException("can't open input stream");
		}
		lengths.putLong(file.length());
		header.write(lengths.array());
		header.write(path);
		out.write(header.toByteArray());
		final int bufferSize = 1024 * 1024;
		byte[] buf = new byte[bufferSize];
		int maxProgress = (int) (file.length() / bufferSize);
		long pos = 0;
		int read;
		reporter.report(name, (int) (pos / bufferSize), maxProgress);
		while (true) {
			read = s.read(buf);
			if (read > 0) {
				pos += read;
				byte[] buf2 = new byte[read];
				System.arraycopy(buf, 0, buf2, 0, read);
				out.write(buf2);
				reporter.report(name, (int) (pos / bufferSize), maxProgress);
			} else {
				break;
			}
		}
	}

	@Override
	public void run() {
		try {
			for (String file : files) {
				DocumentFile entry = root.findFile(file);
				if (entry != null) {
					if (entry.isDirectory()) {
						sendDir(entry, "");
					} else if (entry.isFile()) {
						sendFile(entry, "");
					}
				}
			}
			reporter.report(null, 0, 0);
			ByteBuffer lengths = ByteBuffer.allocate(Integer.BYTES + Long.BYTES).order(ByteOrder.BIG_ENDIAN);
			lengths.putInt(0);
			lengths.putLong(0);
			out.write(lengths.array()); // bye
			out.close();
			success = true;
			Log.d(LOG_TAG, "DirectoryReader finished normally");
		} catch (InterruptedException e) {
			Log.d(LOG_TAG, "DirectoryReader interrupted");
		} catch (IOException e) {
			Log.e(LOG_TAG, "DirectoryReader", e);
		}
	}
}
