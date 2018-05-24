package me.hexian000.masstransfer.io;

// Thread unsafe
public class AverageRateCounter extends RateCounter {
	private long[] values;
	private long sum;
	private int pos;

	public AverageRateCounter(int window) {
		super();
		values = new long[window];
		sum = 0;
		pos = 0;
	}

	@Override
	public long rate() {
		final long rate = super.rate();
		sum += rate - values[pos];
		values[pos] = rate;
		pos++;
		if (pos >= values.length) {
			pos = 0;
		}
		return sum / values.length;
	}
}
