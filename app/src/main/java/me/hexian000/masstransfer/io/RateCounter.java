package me.hexian000.masstransfer.io;

import java.util.concurrent.atomic.AtomicLong;

public class RateCounter {
	private AtomicLong value;
	private long last;

	public RateCounter() {
		value = new AtomicLong(0);
		last = 0;
	}

	public void increase(long value) {
		this.value.addAndGet(value);
	}

	public synchronized long rate() {
		long now = value.get();
		long rate = now - last;
		last = now;
		return rate;
	}
}
