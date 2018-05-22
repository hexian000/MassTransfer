package me.hexian000.masstransfer.io;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

public class BufferInputWrapper extends InputStream {
	private final Buffer buffer;

	public BufferInputWrapper(Buffer b) {
		buffer = b;
	}

	@Override
	public int read() throws IOException {
		byte[] buffer = new byte[1];
		if (read(buffer) > 0) {
			return buffer[0];
		}
		return -1;
	}

	@Override
	public int read(@NonNull byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(@NonNull byte[] b, int off, int len) throws IOException {
		try {
			return buffer.read(b, off, len);
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}
}
