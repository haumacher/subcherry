package com.subcherry.diff;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class Diff {

	public static final String HEADER_SEPARATOR = "===================================================================";
	
	private final String name1;
	private final long revision1;
	private final String name2;
	private final long revision2;
	private final List<Chunk> chunks = new ArrayList<Chunk>();

	public Diff(String name1, long revision1, String name2, long revision2) {
		this.name1 = name1;
		this.revision1 = revision1;
		this.name2 = name2;
		this.revision2 = revision2;
	}

	public void add(Chunk chunk) {
		chunks.add(chunk);
	}
	
	public List<Chunk> getChunks() {
		return chunks;
	}

	public void print(PrintWriter out) {
		out.println(HEADER_SEPARATOR);
		
		out.print("--- ");
		out.print(name1);
		out.print("\t(revision ");
		out.print(revision1);
		out.println(")");
		
		out.print("+++ ");
		out.print(name2);
		out.print("\t(revision ");
		out.print(revision2);
		out.println(")");
		
		for (Chunk chunk : chunks) {
			chunk.print(out);
		}
	}

}

