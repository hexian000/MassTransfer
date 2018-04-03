package me.hexian000.masstransfer.streams;

import android.content.ContentResolver;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static me.hexian000.masstransfer.TransferApp.LOG_TAG;

/*
 * DirectoryReader 是对系统自带的 DocumentFile 的封装
 */

public class DirectoryReader implements Runnable {
	private ProgressReporter reporter;
	private ContentResolver resolver;
	private DocumentFile root;
	private Writer out;

	public DirectoryReader(ContentResolver resolver,
	                       DocumentFile root,
	                       Writer out,
	                       ProgressReporter reporter) {
		this.resolver = resolver;
		this.root = root;
		this.out = out;
		this.reporter = reporter;
	}

	private void sendFile(DocumentFile file, String basePath) throws IOException, InterruptedException {
		if (!file.exists()) return;
		String pathStr;
		byte[] path;
		if (basePath.length() > 0)
			pathStr = basePath + "/" + file.getName();
		else
			pathStr = file.getName();
		path = pathStr.getBytes("UTF-8");
		ByteArrayOutputStream header = new ByteArrayOutputStream();
		ByteBuffer lengths = ByteBuffer.allocate(Integer.BYTES + Long.BYTES).
				order(ByteOrder.BIG_ENDIAN);
		lengths.putInt(path.length);
		if (file.isDirectory()) {
			Log.d(LOG_TAG, "Now at: " + pathStr);
			lengths.putLong(-1); // directory
			header.write(lengths.array());
			header.write(path);
			out.write(header.toByteArray());
			for (DocumentFile f : file.listFiles()) {
				sendFile(f, pathStr);
			}
		} else if (file.isFile() && file.canRead()) {
			final String name = file.getName();
			Log.d(LOG_TAG, "sendFile: " + name +
					" length=" + file.length());
			InputStream s = resolver.openInputStream(file.getUri());
			if (s == null) throw new IOException("can't open input stream");
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
				if (read == -1) break;
				if (read > 0) {
					pos += read;
					byte[] buf2 = new byte[read];
					System.arraycopy(buf, 0, buf2, 0, read);
					out.write(buf2);
					reporter.report(name, (int) (pos / bufferSize), maxProgress);
				}
			}
		}
	}

	@Override
	public void run() {
		try {
			sendFile(root, "");
			ByteBuffer lengths = ByteBuffer.allocate(Integer.BYTES + Long.BYTES).
					order(ByteOrder.BIG_ENDIAN);
			lengths.putInt(0);
			lengths.putLong(0);
			out.write(lengths.array()); // bye
			out.close();
			Log.d(LOG_TAG, "DirectoryReader finished normally");
		} catch (InterruptedException e) {
			Log.d(LOG_TAG, "DirectoryReader interrupted");
		} catch (IOException e) {
			Log.e(LOG_TAG, "DirectoryReader", e);
		}
	}
}
