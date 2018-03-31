package me.hexian000.masstransfer.streams;

import java.io.IOException;

interface Writer {
	void write(byte[] buffer) throws IOException, InterruptedException;
}
