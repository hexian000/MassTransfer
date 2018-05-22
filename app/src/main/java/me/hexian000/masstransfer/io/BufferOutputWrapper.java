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
		byte[] buffer = new byte[1];
		buffer[0] = (byte) b;
		write(buffer);
	}

	@Override
	public void write(@NonNull byte[] b, int off, int len) throws IOException {
		if (off == 0 && len == b.length) {
			write(b);
			return;
		}
		byte[] buf = new byte[len];
		System.arraycopy(b, off, buf, 0, len);
		write(buf);
	}

	@Override
	public void write(@NonNull byte[] b) throws IOException {
		try {
			buffer.write(b);
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void close() {
		buffer.close();
	}
}
