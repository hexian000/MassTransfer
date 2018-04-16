package me.hexian000.masstransfer.io;

/*
 * DirectoryWriter 是对系统自带的 DocumentFile 的封装
 * 基于系统接口，实现文件夹的流化
 *
 * 目录树传输协议基于流式传输实现，无分包概念：
 * 4B文件名长度 | 8B文件长度 | 文件名 | 文件数据流 | (重复...)
 * 若文件名长度 = 文件长度 = 0，表示文件传输完毕
 * 若文件长度 = -1，表示是文件夹
 * 文件名采用UTF-8编码，包含文件的相对路径，并约定以"/"为路径分隔符
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
	private boolean success = false;

	public DirectoryWriter(ContentResolver resolver, DocumentFile root, Reader in, ProgressReporter reporter) {
		this.resolver = resolver;
		this.root = root;
		this.in = in;
		this.reporter = reporter;
	}

	public boolean isSuccess() {
		return success;
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
		Log.d(LOG_TAG, "writeFile: " + path + " length=" + length);
		String name;
		DocumentFile parent = root;
		String[] pathSegments = path.split("/");
		if (pathSegments.length > 1) {
			String[] parentSegments = new String[pathSegments.length - 1];
			System.arraycopy(pathSegments, 0, parentSegments, 0, pathSegments.length - 1);
			parent = makePath(parentSegments);
			name = pathSegments[pathSegments.length - 1];
		} else {
			name = pathSegments[0];
		}
		DocumentFile file = parent.findFile(name);
		if (file != null) {
			file.delete();
		}
		String mime = null;
		int dot = name.lastIndexOf(".");
		if (dot != -1) {
			mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot));
		}
		if (mime == null || "null".equals(mime)) {
			mime = "application/*";
		}

		file = parent.createFile(mime, name);
		OutputStream out = null;
		try {
			if (file != null) {
				out = resolver.openOutputStream(file.getUri());
			} else {
				Log.e(LOG_TAG, "Can't create file mime=" + mime + " name=" + name);
			}
			final int bufferSize = 1024 * 1024;
			long pos = 0;
			int maxProgress = (int) (length / bufferSize);
			reporter.report(name, (int) (pos / bufferSize), maxProgress);
			while (length > 0) {
				byte[] buffer = new byte[(int) Math.min(length, bufferSize)];
				int read = in.read(buffer);
				if (read != buffer.length) {
					throw new EOFException("read=" + read + " buffer=" + buffer.length + " length=" + length);
				}
				if (out != null) {
					out.write(buffer);
				}
				length -= read;
				pos += read;
				reporter.report(name, (int) (pos / bufferSize), maxProgress);
			}
		} catch (InterruptedException ignored) {
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
				ByteBuffer lengths = ByteBuffer.allocate(Integer.BYTES + Long.BYTES).order(ByteOrder.BIG_ENDIAN);
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
			success = true;
			reporter.report(null, 0, 0);
			Log.d(LOG_TAG, "DirectoryWriter finished normally");
		} catch (InterruptedException e) {
			Log.d(LOG_TAG, "DirectoryWriter interrupted");
		} catch (EOFException e) {
			Log.e(LOG_TAG, "DirectoryWriter early EOF", e);
		} catch (IOException e) {
			Log.e(LOG_TAG, "DirectoryWriter", e);
		}
	}
}
