package com.subcherry.diff;

import java.io.PrintWriter;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class Line {

	private final Operation operation;
	private final String content;

	public Line(Operation operation, String content) {
		this.operation = operation;
		this.content = content;
	}

	public Operation getOperation() {
		return operation;
	}

	public String getContent() {
		return content;
	}

	public void print(PrintWriter out) {
		switch (operation) {
			case TAKE: {
				out.print(" ");
				break;
			}
			case ADD: {
				out.print("+");
				break;
			}
			case DELETE: {
				out.print("-");
				break;
			}
		}
		out.println(content);
	}
}

