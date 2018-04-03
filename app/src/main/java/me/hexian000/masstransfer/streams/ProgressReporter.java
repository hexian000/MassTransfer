package me.hexian000.masstransfer.streams;

public interface ProgressReporter {
	void report(String text, int now, int max);
}
