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
	private boolean readSuccess, writeSuccess;
	private long readPos, writePos, size;
	private Thread read, write;
	private InputStream in;
	private OutputStream out;

	Pump(InputStream in, OutputStream out, int buffer, long size) {
		q = new LinkedBlockingQueue<>(buffer);
		this.in = in;
		this.out = out;
		this.size = size;
		readSuccess = false;
		writeSuccess = false;

		// in -> queue
		read = new Thread(new ReadThread());
		// queue -> out
		write = new Thread(new WriteThread());
	}

	void start() {
		read.start();
		write.start();
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

	boolean isRunning() {
		return (read != null && read.isAlive()) || (write != null && write.isAlive());
	}

	boolean isSuccess() {
		return readSuccess && writeSuccess;
	}

	void cancel() {
		if (read != null) read.interrupt();
		if (write != null) write.interrupt();
	}

	private class ReadThread implements Runnable {
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
				q.put(new byte[0]);
				if (readPos != size) {
					Log.d(TransferApp.LOG_TAG, "pump read size mismatch");
				} else {
					readSuccess = true;
				}
			} catch (InterruptedException ignored) {
			} catch (IOException e) {
				write.interrupt();
				Log.e(TransferApp.LOG_TAG, "pump read error", e);
			}
		}
	}

	private class WriteThread implements Runnable {
		public void run() {
			try {
				while (true) {
					byte[] buf = q.take();
					if (buf.length > 0) {
						out.write(buf);
					} else break;
					writePos += buf.length;
				}
				if (writePos == size)
					writeSuccess = true;
			} catch (InterruptedException ignored) {
			} catch (IOException e) {
				read.interrupt();
				Log.e(TransferApp.LOG_TAG, "pump write error", e);
			}
		}
	}
}
