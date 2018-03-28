package me.hexian000.masstransfer.streams;

import java.io.InputStream;

/*
 * RobustSocketInputStream 是对系统自带TCP Socket流的封装
 * TODO 实现数据传输、窗缓存和ACK
 */

class RobustSocketInputStream extends InputStream {
	@Override
	public int read() {
		return 0;
	}
}
