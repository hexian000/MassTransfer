package me.hexian000.masstransfer.io;

/*
 * DirectoryReader 是对系统自带的 DocumentFile 的封装
 * 基于系统接口，实现文件夹的流化
 */

import android.content.ContentResolver;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static me.hexian000.masstransfer.MassTransfer.LOG_TAG;

public class DirectoryReader extends Thread {
	private final ProgressReporter reporter;
	private final ContentResolver resolver;
	private final DocumentFile root;
	private final String[] files;
	private final Channel out;
	private final BufferPool bufferPool;
	private boolean success = false;

	public DirectoryReader(ContentResolver resolver, DocumentFile root, String[] files, Channel out,
			ProgressReporter reporter, BufferPool bufferPool) {
		this.resolver = resolver;
		this.root = root;
		this.files = files;
		this.out = out;
		this.reporter = reporter;
		this.bufferPool = bufferPool;
	}

	public boolean isSuccess() {
		return success;
	}

	private void sendDir(final DocumentFile dir, final String basePath) throws IOException, InterruptedException {
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
		Log.d(LOG_TAG, "Now at: " + pathStr);
		byte[] path = pathStr.getBytes("UTF-8");
		final ByteBuffer header = bufferPool.pop();
		header.order(ByteOrder.BIG_ENDIAN)
				.putInt(path.length)
				.putLong(-1) // directory
				.put(path)
				.flip();
		out.write(header);
		for (DocumentFile f : dir.listFiles()) {
			if (f.isFile()) {
				sendFile(f, pathStr);
			} else if (f.isDirectory()) {
				sendDir(f, pathStr);
			}
		}
	}

	private void sendFile(final DocumentFile file, final String basePath) throws IOException, InterruptedException {
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
		final ByteBuffer header = bufferPool.pop();
		header.order(ByteOrder.BIG_ENDIAN)
				.putInt(path.length)
				.putLong(length)
				.put(path)
				.flip();
		out.write(header);
		final String name = file.getName();
		Log.d(LOG_TAG, "sendFile: " + name + " length=" + length);
		try (final InputStream in = resolver.openInputStream(file.getUri())) {
			if (in == null) {
				throw new IOException("can't open input stream");
			}
			long pos = 0;
			reporter.report(name, 0, 0);
			if (length > 0) {
				int read;
				while (true) {
					ByteBuffer buf = bufferPool.pop();
					read = in.read(buf.array(), 0, buf.capacity());
					if (read < 1) {
						bufferPool.push(buf);
						break;
					}
					buf.limit(read);
					pos += read;
					out.write(buf);
					reporter.report(name, pos, length);
				}
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
			ByteBuffer buffer = bufferPool.pop();
			buffer.putInt(0)
					.putLong(0)
					.flip();
			out.write(buffer); // bye
			out.close();
			success = true;
			Log.d(LOG_TAG, "DirectoryReader finished normally");
		} catch (IOException e) {
			Log.e(LOG_TAG, "DirectoryReader", e);
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "DirectoryReader Interrupted", e);
		}
	}
}
