package me.hexian000.masstransfer.io;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;

public class BufferOutputWrapper extends OutputStream {
	private final Buffer buffer;

	public BufferOutputWrapper(Buffer buffer) {
		this.buffer = buffer;
	}

	@Override
	public void write(int b) throws IOException {
		byte[] buffer = new byte[1];
		buffer[0] = (byte) b;
		write(buffer);
	}

	@Override
	public void write(@NonNull byte[] buffer) throws IOException {
		try {
			this.buffer.write(buffer);
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void close() {
		buffer.close();
	}
}
