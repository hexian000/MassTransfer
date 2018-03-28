package me.hexian000.masstransfer.streams;

import java.io.InputStream;

/*
 * DirectoryInputStream 是对系统自带的 FileInputStream 的封装
 * TODO 基于系统接口，实现文件夹的流化
 */

class DirectoryInputStream extends InputStream {
	@Override
	public int read() {
		return 0;
	}
}
