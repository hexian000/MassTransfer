package me.hexian000.masstransfer.io;

public interface ProgressReporter {
	void report(String text, long now, long max);
}
