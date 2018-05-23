package me.hexian000.masstransfer.io;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;

public class BufferOutputWrapper extends OutputStream {
	private final Buffer buffer;

	public BufferOutputWrapper(Buffer b) {
		buffer = b;
	}

	@Override
	public void write(int b) throws IOException {
		throw new IOException("Not implemented");
	}

	@Override
	public void write(@NonNull byte[] b, int off, int len) throws IOException {
		byte[] buf = new byte[len];
		System.arraycopy(b, off, buf, 0, len);
		try {
			buffer.write(buf);
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void close() {
		buffer.close();
	}
}
