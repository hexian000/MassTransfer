package me.hexian000.masstransfer.streams;

import android.util.Log;
import me.hexian000.masstransfer.TransferApp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/*
 * 管线设计如下：
 * 发送方：[文件夹] -> [DirectoryInputStream] -> [Pump] -> [RobustSocketOutputStream] -> [网络]
 * 接收方：[网络] -> [RobustSocketInputStream] -> [Pump] -> [DirectoryOutputStream] -> [文件夹]
 */

final class Pump {
	private BlockingQueue<byte[]> q;
	private boolean success;
	private long readPos, writePos, size;
	private Thread read, write;
	private InputStream in;
	private OutputStream out;

	void start(InputStream in, OutputStream out, long size) {
		q = new LinkedBlockingQueue<>(1024);
		this.in = in;
		this.out = out;
		this.size = size;
		success = true;

		// in -> queue
		read = new ReadThread();
		// queue -> out
		write = new WriteThread();

		read.start();
		write.start();

		new Thread(() -> { // cleanups
			try {
				read.join();
				write.join();
				read = null;
				write = null;
				q = null;
			} catch (InterruptedException ignored) {
			}
		}).start();
	}

	int getBufferSize() {
		return q.size();
	}

	long getReadPos() {
		return readPos;
	}

	long getWritePos() {
		return writePos;
	}

	boolean isAlive() {
		return (read != null && read.isAlive()) || (write != null && write.isAlive());
	}

	boolean isSuccess() {
		return success;
	}

	void cancel() {
		if (read != null) read.interrupt();
		if (write != null) write.interrupt();
	}

	private class ReadThread extends Thread {
		@Override
		public void run() {
			try {
				byte[] buf = new byte[1024 * 1024];
				readPos = 0;
				int read;
				while (readPos < size) {
					read = in.read(buf);
					if (read > 0) {
						if (read == buf.length && readPos + read <= size)
							q.put(buf);
						else {
							long length = size - readPos;
							if (length < read) read = (int) length;
							byte[] block = new byte[read];
							System.arraycopy(buf, 0, block, 0, read);
							q.put(block);
						}
					} else break;
					readPos += read;
				}
				if (readPos != size) {
					success = false;
					Log.d(TransferApp.LOG_TAG, "pump read size mismatch");
				}
				write.interrupt();
			} catch (InterruptedException ignored) {
			} catch (IOException e) {
				success = false;
				write.interrupt();
				Log.e(TransferApp.LOG_TAG, "pump read error", e);
			}
		}
	}

	private class WriteThread extends Thread {
		@Override
		public void run() {
			try {
				while (true) {
					byte[] buf = q.take();
					if (buf.length > 0) {
						out.write(buf);
					} else break;
					writePos += buf.length;
				}
			} catch (InterruptedException ignored) {
			} catch (IOException e) {
				success = false;
				read.interrupt();
				Log.e(TransferApp.LOG_TAG, "pump write error", e);
			}
		}
	}
}
