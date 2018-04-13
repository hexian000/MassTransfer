package me.hexian000.masstransfer.io;

import java.io.IOException;

public interface Reader {
	int read(byte[] buffer) throws IOException, InterruptedException;
}
