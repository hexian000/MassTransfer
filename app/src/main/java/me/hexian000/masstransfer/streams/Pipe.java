package me.hexian000.masstransfer.streams;

import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TransferQueue;

public class Pipe implements Reader, Writer {
	private int limit;
	private Semaphore capacity;
	private TransferQueue<byte[]> q;
	private byte[] current = null;
	private int offset = 0;
	private boolean closed = false;

	public Pipe(int capacity) {
		q = new LinkedTransferQueue<>();
		limit = capacity;
		this.capacity = new Semaphore(capacity);
	}

	public long getSize() {
		return limit - capacity.availablePermits();
	}

	@Override
	public synchronized int read(byte[] buffer) throws InterruptedException {
		int read = 0;
		while (read < buffer.length) {
			if (current == null) {
				if (closed && q.size() == 0)
					break;
				current = q.take();
			} else {
				int count = Math.min(current.length - offset, buffer.length - read);
				System.arraycopy(current, offset, buffer, read, count);
				offset += count;
				read += count;
				if (offset == current.length) {
					current = null;
					offset = 0;
				}
			}
		}
		capacity.release(read);
		return read;
	}

	@Override
	public void write(byte[] buffer) throws InterruptedException {
		if (buffer.length > 0) {
			q.transfer(buffer);
			capacity.acquire(buffer.length);
		}
	}

	@Override
	public void close() {
		closed = true;
	}
}
