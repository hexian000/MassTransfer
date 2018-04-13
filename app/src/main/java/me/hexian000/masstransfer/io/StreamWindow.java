package me.hexian000.masstransfer.io;

import java.util.concurrent.Semaphore;

public class StreamWindow {
	private byte[] window;
	private int offset;
	private long pos;
	private Semaphore usage;

	public StreamWindow(int size) {
		window = new byte[size];
		pos = 0;
		offset = 0;
		usage = new Semaphore(size);
	}

	public synchronized void send(byte[] data) throws InterruptedException {
		if (data.length > window.length)
			throw new IllegalArgumentException();
		usage.acquire(data.length);
		if (offset + data.length <= window.length) {
			System.arraycopy(data, 0, window, offset, data.length);
			offset += data.length;
		} else {
			if (offset + data.length <= window.length) {
				System.arraycopy(data, 0, window, offset, data.length);
				offset += data.length;
			} else {
				int copied = window.length - offset;
				System.arraycopy(data, 0, window, offset, copied);
				offset = data.length - copied;
				System.arraycopy(data, copied, window, 0, offset);
			}
		}
	}

	public synchronized byte[] rollback() {
		final int size = window.length - usage.availablePermits();
		byte[] resend = new byte[size];
		if (offset + size <= window.length) {
			System.arraycopy(window, offset, resend, 0, size);
		} else {
			int copied = window.length - offset;
			System.arraycopy(window, offset, resend, 0, copied);
			System.arraycopy(window, 0, resend, copied, size - copied);
		}
		return resend;
	}

	public synchronized void ack(long newPos) {
		if (newPos < pos) {
			throw new IllegalArgumentException("newPos");
		}
		if (pos > newPos) {
			usage.release((int) (pos - newPos));
		}
		pos = newPos;
	}
}
