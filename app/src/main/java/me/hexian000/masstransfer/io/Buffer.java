package me.hexian000.masstransfer.io;

import android.support.annotation.NonNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

// for single-producer and single-consumer only
public class Buffer {
	private final int limit;
	private final Semaphore capacity;
	private final BlockingQueue<byte[]> q;
	private byte[] current = null;
	private int offset = 0;
	private boolean closed = false, eof = false;

	public Buffer(int capacity) {
		q = new LinkedBlockingQueue<>();
		limit = capacity;
		this.capacity = new Semaphore(capacity);
	}

	public int getSize() {
		return limit - capacity.availablePermits();
	}

	public int read(@NonNull byte[] b, int off, int len) throws InterruptedException {
		if (eof) {
			return -1;
		}
		int read = 0;
		while (read < len) {
			if (current == null) {
				current = q.take();
				if (current.length == 0) {
					eof = true;
					if (read == 0) {
						return -1;
					}
					break;
				}
			}
			int count = Math.min(current.length - offset, len - read);
			System.arraycopy(current, offset, b, off + read, count);
			offset += count;
			read += count;
			if (offset >= current.length) {
				current = null;
				offset = 0;
			}
		}
		capacity.release(read);
		return read;
	}

	public void write(@NonNull byte[] buffer) throws InterruptedException {
		if (closed) {
			throw new IllegalStateException("pipe is closed");
		}
		if (buffer.length > 0) {
			capacity.acquire(buffer.length);
			q.put(buffer);
		}
	}

	public void close() {
		if (!closed) {
			closed = true;
			try {
				q.put(new byte[0]);
			} catch (InterruptedException ignored) {
			}
		}
	}
}
