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
import android.support.annotation.NonNull;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static me.hexian000.masstransfer.MassTransfer.LOG_TAG;

public class DirectoryWriter extends Thread {
	private final ProgressReporter reporter;
	private final ContentResolver resolver;
	private final DocumentFile root;
	private final Channel in;
	private final BufferPool bufferPool;
	private ByteBuffer current;
	private boolean success = false;

	public DirectoryWriter(ContentResolver resolver, DocumentFile root, Channel in, ProgressReporter reporter,
			BufferPool bufferPool) {
		this.resolver = resolver;
		this.root = root;
		this.in = in;
		this.reporter = reporter;
		this.bufferPool = bufferPool;
	}

	public boolean isSuccess() {
		return success;
	}

	private DocumentFile makePath(@NonNull String[] segments) {
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
			if (current == null) {
				return null;
			}
			parent = current;
		}
		return parent;
	}

	private void writeFile(final String path, final long length) throws IOException, InterruptedException {
		if (length == -1) { // is directory
			Log.d(LOG_TAG, "Now at: " + path);
			makePath(path.split(Pattern.quote("/")));
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
		DocumentFile file = null;
		if (parent != null) {
			file = parent.findFile(name);
			if (file != null) {
				file.delete();
			}
		}
		String mime = null;
		int dot = name.lastIndexOf(".");
		if (dot != -1) {
			mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot));
		}
		if (mime == null || "null".equals(mime)) {
			mime = "application/*";
		}

		if (parent != null) {
			file = parent.createFile(mime, name);
		}

		if (length == 0) {
			return;
		}

		OutputStream out = null;
		try {
			if (file != null) {
				out = resolver.openOutputStream(file.getUri());
			} else {
				Log.e(LOG_TAG, "Can't create file mime=" + mime + " name=" + name);
			}
			long pos = 0;
			reporter.report(name, 0, 0);
			while (pos < length) {
				readAtLeast(0);
				if (out != null) {
					out.write(current.array(), current.arrayOffset() + current.position(), current.remaining());
				}
				pos += current.remaining();
				bufferPool.push(current);
				current = null;
				reporter.report(name, pos, length);
			}
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	private void readAtLeast(int size) throws InterruptedException {
		if (current == null) {
			current = in.read();
		}
		if (current.remaining() == 0) {
			bufferPool.push(current);
			current = in.read();
		}
		if (current.remaining() >= size) {
			return;
		}

		int sum = 0;
		List<ByteBuffer> list = new ArrayList<>();
		list.add(current);
		while (sum < size) {
			ByteBuffer next = in.read();
			list.add(next);
			sum += next.remaining();
		}

		current = ByteBuffer.allocate(sum);
		for (ByteBuffer b : list) {
			current.put(b.array(), b.arrayOffset() + b.position(), b.remaining());
			bufferPool.push(b);
		}
	}

	@Override
	public void run() {
		try {
			do {
				readAtLeast(Integer.BYTES + Long.BYTES);
				int nameLen = current.getInt();
				long fileLen = current.getLong();
				if (nameLen == 0 && fileLen == 0) {
					Log.d(LOG_TAG, "protocol bye");
					break; // bye
				}
				if (nameLen < 0 || nameLen > 65535 || fileLen < -1) {
					Log.wtf(LOG_TAG, "BUG: invalid header, nameLen=" + nameLen + " fileLen=" + fileLen);
					return;
				}
				readAtLeast(nameLen);
				String path = new String(
						current.array(),
						current.arrayOffset() + current.position(),
						nameLen,
						"UTF-8");
				current.position(current.position() + nameLen);
				writeFile(path, fileLen);
			} while (true);
			success = true;
			reporter.report(null, 0, 0);
			Log.d(LOG_TAG, "DirectoryWriter finished normally");
		} catch (EOFException e) {
			Log.e(LOG_TAG, "DirectoryWriter early EOF", e);
		} catch (IOException e) {
			Log.e(LOG_TAG, "DirectoryWriter", e);
		} catch (InterruptedException e) {
			Log.e(LOG_TAG, "DirectoryWriter Interrupted", e);
		}
	}
}
