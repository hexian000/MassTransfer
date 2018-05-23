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
		throw new IOException("Not implemented");
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
