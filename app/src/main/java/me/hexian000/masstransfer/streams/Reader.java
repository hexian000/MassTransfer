package me.hexian000.masstransfer.streams;

import java.io.IOException;

public interface Reader {
	int read(byte[] buffer) throws IOException, InterruptedException;
}
