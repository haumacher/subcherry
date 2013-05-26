package com.subcherry.utils;

import java.util.Iterator;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class StringSource {

	private Iterator<String> it;
	private int line;
	private String content;
	
	public StringSource(Iterable<String> lines) {
		it = lines.iterator();
	}
	
	public String next() {
		if (it.hasNext()) {
			content = it.next();
			line++;
		} else {
			content = null;
		}
		return content;
	}
	
	public String last() {
		return content;
	}

	public boolean has() {
		return content != null;
	}

	public int getLine() {
		return line;
	}
}

