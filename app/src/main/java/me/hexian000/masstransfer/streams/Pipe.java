package me.hexian000.masstransfer.streams;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Pipe implements Reader, Writer {
	private BlockingQueue<byte[]> q;
	private byte[] current = null;
	private int offset = 0;

	public Pipe(int size) {
		q = new LinkedBlockingQueue<>(size);
	}

	@Override
	public synchronized int read(byte[] buffer) throws InterruptedException {
		int read = 0;
		while (read < buffer.length) {
			if (current == null) {
				current = q.take();
				if (current.length == 0)
					break;
			} else {
				int count = Math.min(current.length - offset, buffer.length - read);
				System.arraycopy(current, offset, buffer, read, count);
				offset += count;
				read += count;
				if (offset == current.length)
					current = null;
			}
		}
		return read;
	}

	@Override
	public void write(byte[] buffer) throws InterruptedException {
		q.put(buffer);
	}
}
