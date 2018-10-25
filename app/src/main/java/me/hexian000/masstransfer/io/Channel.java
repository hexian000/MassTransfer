package me.hexian000.masstransfer.io;

import android.support.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

// for single-producer and single-consumer only
public class Channel {
	private final int capacity;
	private final Semaphore available;
	private final BlockingQueue<ByteBuffer> q;
	private boolean closed = false;

	public Channel(int capacity) {
		q = new LinkedBlockingQueue<>();
		this.capacity = capacity;
		this.available = new Semaphore(capacity);
	}

	public int getAvailable() {
		return capacity - available.availablePermits();
	}

	public int getCapacity() {
		return capacity;
	}

	public ByteBuffer read() throws InterruptedException {
		if (closed && q.isEmpty()) {
			return null;
		}
		ByteBuffer b = q.take();
		available.release(b.capacity());
		return b;
	}

	public void write(@NonNull ByteBuffer buffer) throws InterruptedException {
		if (closed) {
			throw new IllegalStateException("channel is closed");
		}
		available.acquire(buffer.capacity());
		q.put(buffer);
	}

	public void close() {
		closed = true;
	}

}
