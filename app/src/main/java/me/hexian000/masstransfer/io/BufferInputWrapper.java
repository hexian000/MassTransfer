package me.hexian000.masstransfer.io;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

public class BufferInputWrapper extends InputStream {
	private final Buffer buffer;

	public BufferInputWrapper(Buffer buffer) {
		this.buffer = buffer;
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
	public int read(@NonNull byte[] buffer) throws IOException {
		try {
			return this.buffer.read(buffer);
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}
}
