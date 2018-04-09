package me.hexian000.masstransfer.streams;

import java.util.concurrent.atomic.AtomicLong;

public class AvgRateCounter {
	private AtomicLong sum;
	private long[] values;
	private int size, p;

	public AvgRateCounter(int size) {
		this.size = size;
		values = new long[size];
		for (int i = 0; i < size; i++)
			values[i] = 0;
		sum = new AtomicLong(0);
		p = 0;
	}

	public synchronized void push(long value) {
		sum.addAndGet(value - values[p]);
		values[p] = value;
		p++;
		if (p >= size) p -= size;
	}

	public double rate() {
		return (double) sum.get() / (double) size;
	}
}
