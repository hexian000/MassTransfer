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
	private ContentResolver resolver;
	private DocumentFile root;
	private Writer out;

	public DirectoryReader(ContentResolver resolver,
	                       DocumentFile root,
	                       Writer out) {
		this.resolver = resolver;
		this.root = root;
		this.out = out;
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
				sendFile(f, basePath);
			}
		} else if (file.isFile() && file.canRead()) {
			Log.d(LOG_TAG, "sendFile: " + pathStr);
			InputStream s = resolver.openInputStream(file.getUri());
			if (s == null) throw new IOException("can't open input stream");
			lengths.putLong(file.length());
			header.write(lengths.array());
			header.write(path);
			out.write(header.toByteArray());
			byte[] buf = new byte[1024 * 1024];
			int read;
			do {
				read = s.read(buf);
				if (read == 0) break;
				out.write(buf);
			} while (read == buf.length);
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
			Log.d(LOG_TAG, "Send finished normally");
		} catch (IOException | InterruptedException e) {
			Log.e(LOG_TAG, "DirectoryWriter", e);
		}
	}
}
