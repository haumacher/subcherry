package com.subcherry.utils;

import java.io.IOException;
import java.io.Writer;

public class TeeWriter extends Writer {

	private final Writer _writer1;
	private final Writer _writer2;

	public TeeWriter(Writer writer1, Writer writer2) {
		_writer1 = writer1;
		_writer2 = writer2;
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		_writer1.write(cbuf, off, len);
		_writer2.write(cbuf, off, len);
	}

	@Override
	public void flush() throws IOException {
		_writer1.flush();
		_writer2.flush();
	}

	@Override
	public void close() throws IOException {
		try {
			_writer1.close();
		} finally {
			_writer2.close();
		}
		
	}

}
