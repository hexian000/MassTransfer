package me.hexian000.masstransfer.io;

public interface ProgressReporter {
	void report(String text, int now, int max);
}
