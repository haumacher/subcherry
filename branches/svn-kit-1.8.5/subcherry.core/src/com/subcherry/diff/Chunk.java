package com.subcherry.diff;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class Chunk {

	private final int start1;
	private final int length1;
	private final int start2;
	private final int length2;
	
	private final List<Line> lines = new ArrayList<Line>(); 

	public Chunk(int start1, int length1, int start2, int length2) {
		this.start1 = start1;
		this.length1 = length1;
		this.start2 = start2;
		this.length2 = length2;
	}
	
	public int getStart1() {
		return start1;
	}

	public int getLength1() {
		return length1;
	}

	public int getStart2() {
		return start2;
	}

	public int getLength2() {
		return length2;
	}

	public List<Line> getLines() {
		return lines;
	}

	public void take(String content) {
		lines.add(new Line(Operation.TAKE, content));
	}
	
	public void add(String content) {
		lines.add(new Line(Operation.ADD, content));
	}
	
	public void delete(String content) {
		lines.add(new Line(Operation.DELETE, content));
	}

	public void print(PrintWriter out) {
		out.print("@@ -");
		out.print(start1);
		out.print(",");
		out.print(length1);
		out.print(" +");
		out.print(start2);
		out.print(",");
		out.print(length2);
		out.print(" @@");

		for (Line line : lines) {
			line.print(out);
		}
	}

}

