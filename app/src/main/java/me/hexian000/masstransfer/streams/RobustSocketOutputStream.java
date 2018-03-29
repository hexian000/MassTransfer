package me.hexian000.masstransfer.streams;

import java.io.OutputStream;

/*
 * RobustSocketOutputStream 是对系统自带TCP Socket流的封装
 * TODO 实现数据传输、窗缓存和ACK
 */

public class RobustSocketOutputStream extends OutputStream {
	private RobustSocket socket;

	RobustSocketOutputStream(RobustSocket socket) {
		this.socket = socket;
	}

	@Override
	public void write(int b) {

	}
}
