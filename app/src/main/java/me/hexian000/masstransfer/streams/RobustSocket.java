package me.hexian000.masstransfer.streams;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Timer;

public class RobustSocket {
	private final static byte TYPE_DATA = 0x00;
	private final static byte TYPE_ACK = 0x01;

	DataFrame frame;
	InputStream in;
	OutputStream out;
	byte[][] window;
	int p = 0;
	long pos = 0, ack = 0;
	SocketAddress endpoint;
	Socket socket;
	Timer timer;

	RobustSocket(SocketAddress endpoint, int windowSize) throws IOException {
		reconnect();
		in = socket.getInputStream();
		out = socket.getOutputStream();
		window = new byte[windowSize][];
		frame = new DataFrame();
	}

	void reconnect() throws IOException {
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException ignored) {
			}
		}
		socket = new Socket();
		socket.connect(endpoint, 4000);
	}

	public void close() {

	}

	public InputStream getInputStream() {
		return new RobustSocketInputStream(this);
	}

	public OutputStream getOutputStream() {
		return new RobustSocketOutputStream(this);
	}

	private class DataFrame { // 数据帧
		byte type; // 协议字，标识数据帧的种类
		boolean triggerResend;
		byte[] data; // 载荷

		void readFrom(InputStream i) throws IOException {
			int protocolWord = i.read();
			if (protocolWord == -1)
				throw new EOFException();
			type = (byte) protocolWord;
			switch (type) {
				case TYPE_DATA: {
					/*
					 * type | length | data
					 * 1    | 2      | ?
					 */
					ByteBuffer buf = ByteBuffer.allocate(Short.BYTES).
							order(ByteOrder.BIG_ENDIAN);
					int read = i.read(buf.array());
					if (read != 4)
						throw new EOFException();
					int length = ((int) buf.getShort() & 0xffff);
					data = new byte[length];
					read = i.read(data);
					if (read != data.length)
						throw new EOFException();
					triggerResend = false;
				}
				break;
				case TYPE_ACK: {
					/*
					 * type | ack
					 * 1    | 8
					 */
					ByteBuffer buf = ByteBuffer.allocate(8).
							order(ByteOrder.BIG_ENDIAN);
					int read = i.read(buf.array());
					if (read != 8)
						throw new EOFException();
					ack = buf.getLong();
					triggerResend = false;
				}
				break;
				default:
					throw new IOException("unknown protocol word");
			}
		}

		void writeData(OutputStream o) throws IOException {
			ByteBuffer buf = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES).
					order(ByteOrder.BIG_ENDIAN);
			buf.put(TYPE_DATA);
			buf.putShort((short) data.length);
			o.write(buf.array());
			o.write(data);
		}

		void writeAck(OutputStream o) throws IOException {
			ByteBuffer buf = ByteBuffer.allocate(Byte.BYTES + Long.BYTES).
					order(ByteOrder.BIG_ENDIAN);
			buf.put(TYPE_ACK);
			buf.putLong(ack);
			o.write(buf.array());
		}
	}
}
