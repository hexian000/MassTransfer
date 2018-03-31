package me.hexian000.masstransfer.streams;

import android.content.ContentResolver;
import android.support.v4.provider.DocumentFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/*
 * DirectoryReader 是对系统自带的 DocumentFile 的封装
 */

class DirectoryReader implements Runnable {
	ContentResolver resolver;
	DocumentFile root;
	Writer out;

	DirectoryReader(ContentResolver resolver,
	                DocumentFile root,
	                Writer out) {
		this.resolver = resolver;
		this.root = root;
		this.out = out;
	}

	private void sendFile(DocumentFile file, String basePath) throws IOException, InterruptedException {
		if (!file.exists()) return;
		byte[] path;
		if (basePath.length() > 0)
			path = (basePath + "/" + file.getName()).getBytes("UTF-8");
		else
			path = file.getName().getBytes("UTF-8");
		ByteArrayOutputStream header = new ByteArrayOutputStream();
		ByteBuffer lengths = ByteBuffer.allocate(Integer.BYTES + Long.BYTES).
				order(ByteOrder.BIG_ENDIAN);
		lengths.putInt(path.length);
		if (file.isDirectory()) {
			lengths.putLong(-1); // directory
			header.write(lengths.array());
			header.write(path);
			out.write(header.toByteArray());
			for (DocumentFile f : file.listFiles()) {
				sendFile(f, basePath);
			}
		} else if (file.isFile() && file.canRead()) {
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
				out.write(buf);
			} while (read == buf.length);
		}
	}

	@Override
	public void run() {
		try {
			sendFile(root, "");
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
}
