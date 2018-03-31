package me.hexian000.masstransfer.streams;

import java.io.IOException;

interface Reader {
	int read(byte[] buffer) throws IOException, InterruptedException;
}
