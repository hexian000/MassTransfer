package me.hexian000.masstransfer.streams;

/*
 * DirectoryWriter 是对系统自带的 DocumentFile 的封装
 * 基于系统接口，实现文件夹的流化
 */

import android.content.ContentResolver;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

public class DirectoryWriter implements Runnable {
	private ProgressReporter reporter;
	private ContentResolver resolver;
	private DocumentFile root;
	private Reader in;

	public DirectoryWriter(ContentResolver resolver,
	                       DocumentFile root,
	                       Reader in,
	                       ProgressReporter reporter) {
		this.resolver = resolver;
		this.root = root;
		this.in = in;
		this.reporter = reporter;
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

	private void writeFile(String path, long length) throws IOException {
		if (length == -1) { // is directory
			Log.d(LOG_TAG, "Now at: " + path);
			makePath(path.split("/"));
			return;
		}
		// is File
		Log.d(LOG_TAG, "writeFile: " + path +
				" length=" + length);
		String name;
		DocumentFile parent = root;
		String[] pathSegments = path.split("/");
		if (pathSegments.length > 1) {
			String[] parentSegments = new String[pathSegments.length - 1];
			System.arraycopy(pathSegments, 0,
					parentSegments, 0,
					pathSegments.length - 1);
			parent = makePath(parentSegments);
			name = pathSegments[pathSegments.length - 1];
		} else {
			name = pathSegments[0];
		}
		DocumentFile file = parent.findFile(name);
		if (file != null) {
			file.delete();
		}
		String mime = "application/*";
		if (name.contains("."))
			mime = MimeTypeMap.getSingleton().
					getMimeTypeFromExtension(
							name.substring(name.lastIndexOf("."))
					);

		file = parent.createFile(mime, name);
		OutputStream out = null;
		try {
			out = resolver.openOutputStream(file.getUri());
			if (out == null) {
				throw new NullPointerException();
			}
			final int bufferSize = 1024 * 1024;
			long pos = 0;
			int maxProgress = (int) (length / bufferSize);
			reporter.report(name, (int) (pos / bufferSize), maxProgress);
			while (length > 0) {
				byte[] buffer = new byte[(int) Math.min(length, bufferSize)];
				int read = in.read(buffer);
				if (read != buffer.length)
					throw new EOFException();
				out.write(buffer);
				length -= read;
				pos += read;
				reporter.report(name, (int) (pos / bufferSize), maxProgress);
			}
		} catch (InterruptedException ignored) {
		} catch (Throwable e) {
			Log.e(LOG_TAG, "writing file error: ", e);
		} finally {
			if (out != null) {
				out.close();
			}
		}
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
					Log.e(LOG_TAG, "EOF when reading header");
					return;
				}
				int nameLen = lengths.getInt();
				long fileLen = lengths.getLong();
				if (nameLen == 0 && fileLen == 0) {
					Log.d(LOG_TAG, "protocol bye");
					break; // bye
				}
				if (nameLen > 65535) {
					Log.e(LOG_TAG, "invalid header");
					return;
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
			reporter.report(null, 0, 0);
			Log.d(LOG_TAG, "DirectoryWriter finished normally");
		} catch (InterruptedException e) {
			Log.d(LOG_TAG, "DirectoryWriter interrupted");
		} catch (IOException e) {
			Log.e(LOG_TAG, "DirectoryWriter", e);
		}
	}
}
