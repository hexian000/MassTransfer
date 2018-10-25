package me.hexian000.masstransfer.io;

import java.nio.ByteBuffer;
import java.util.Stack;

public class BufferPool {
	private final int bufferSize;
	private final Object locker = new Object();
	private final Stack<ByteBuffer> pool = new Stack<>();

	public BufferPool(int bufferSize) {
		this.bufferSize = bufferSize;
	}

	public void push(ByteBuffer buf) {
		if (buf.capacity() != bufferSize) {
			return;
		}
		synchronized (locker) {
			pool.push(buf);
		}
	}

	public ByteBuffer pop() {
		synchronized (locker) {
			if (pool.empty()) {
				return ByteBuffer.allocate(bufferSize);
			} else {
				final ByteBuffer b = pool.pop();
				b.clear();
				return b;
			}
		}
	}
}
