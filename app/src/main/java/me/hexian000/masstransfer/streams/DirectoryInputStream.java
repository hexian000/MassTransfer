package me.hexian000.masstransfer.streams;

import android.content.ContentResolver;
import android.support.v4.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/*
 * DirectoryInputStream 是对系统自带的 DocumentFile 的封装
 */

class DirectoryInputStream extends InputStream {
	ContentResolver resolver;
	Iterator<DocumentFile> files;
	InputStream baseStream;

	DirectoryInputStream(ContentResolver resolver,
	                     Iterator<DocumentFile> files) {
		this.resolver = resolver;
		this.files = files;
	}


	@Override
	public int read() throws IOException {
		if (baseStream == null) {
			if (files.hasNext()) {
				DocumentFile file = files.next();
				if (file.exists() && file.isFile() && file.canRead()) {
					baseStream = resolver.openInputStream(file.getUri());
				}
			} else {
				return -1;
			}
		}
		int data = baseStream.read();
		if (data != -1)
			return data;
		else {
			baseStream.close();
			baseStream = null;
			return read();
		}
	}
}
