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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static me.hexian000.masstransfer.MassTransfer.LOG_TAG;

public class DirectoryReader extends Thread {
	private final ProgressReporter reporter;
	private final ContentResolver resolver;
	private final DocumentFile root;
	private final String[] files;
	private final OutputStream out;
	private boolean success = false;

	public DirectoryReader(ContentResolver resolver, DocumentFile root, String[] files, OutputStream out,
			ProgressReporter reporter) {
		this.resolver = resolver;
		this.root = root;
		this.files = files;
		this.out = out;
		this.reporter = reporter;
	}

	public boolean isSuccess() {
		return success;
	}

	private void sendDir(final DocumentFile dir, final String basePath) throws IOException {
		if (!dir.exists()) {
			return;
		}
		String pathStr = dir.getName();
		if (pathStr == null) {
			return;
		}
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

	private void sendFile(final DocumentFile file, final String basePath) throws IOException {
		if (!file.exists() || !file.canRead()) {
			return;
		}
		String pathStr = file.getName();
		if (pathStr == null) {
			return;
		}
		if (pathStr.startsWith(".")) {
			return; // ignore hidden
		}
		if (basePath.length() > 0) {
			pathStr = basePath + "/" + pathStr;
		}
		final byte[] path = pathStr.getBytes("UTF-8");
		final long length = file.length();
		out.write(ByteBuffer.allocate(Integer.BYTES + Long.BYTES)
				.order(ByteOrder.BIG_ENDIAN)
				.putInt(path.length)
				.putLong(length)
				.array());
		out.write(path);
		final String name = file.getName();
		Log.d(LOG_TAG, "sendFile: " + name + " length=" + length);
		try (final InputStream in = resolver.openInputStream(file.getUri())) {
			if (in == null) {
				throw new IOException("can't open input stream");
			}
			final byte[] buf = new byte[512 * 1024];
			long pos = 0;
			reporter.report(name, 0, 0);
			if (length > 0) {
				int read;
				do {
					read = in.read(buf);
					if (read > 0) {
						pos += read;
						out.write(buf, 0, read);
						reporter.report(name, pos, length);
					}
				} while (read >= 0);
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
		} catch (IOException e) {
			Log.e(LOG_TAG, "DirectoryReader", e);
		}
	}
}
