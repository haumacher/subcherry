package com.subcherry.diff;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.subcherry.utils.StringSource;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class DiffParser {

	private static final Pattern NAME1_PATTERN = Pattern.compile("--- ([^\\t]+)\\t\\(revision (\\d+)\\)");
	private static final Pattern NAME2_PATTERN = Pattern.compile("\\+\\+\\+ ([^\\t]+)\\t\\(revision (\\d+)\\)");
	private static final Pattern PLUS_MINUS_PATTERN = Pattern.compile("@@ -(\\d+),(\\d+) \\+(\\d+),(\\d+) @@");

	public static Diff parse(Iterable<String> lines) {
		
		StringSource source = new StringSource(lines);
		String headerLine;
		while ((headerLine = source.next()) != null) {
			if (headerLine.equals(Diff.HEADER_SEPARATOR)) {
				Matcher name1Matcher = NAME1_PATTERN.matcher(source.next());
				if (!name1Matcher.matches()) {
					throw error("Name 1 expected in line " + source.getLine() + ".");
				}
				String name1 = name1Matcher.group(1);
				long revision1 = Long.parseLong(name1Matcher.group(2));
				
				Matcher name2Matcher = NAME2_PATTERN.matcher(source.next());
				if (!name2Matcher.matches()) {
					throw error("Name 2 expected in line " + source.getLine() + ".");
				}
				String name2 = name1Matcher.group(1);
				long revision2 = Long.parseLong(name1Matcher.group(2));
				
				Diff diff = new Diff(name1, revision1, name2, revision2);

				String chunkHeaderLine;
				while ((chunkHeaderLine = source.next()) != null) {
					Matcher plusMinusMatcher = PLUS_MINUS_PATTERN.matcher(chunkHeaderLine);
					if (!plusMinusMatcher.matches()) {
						throw error("Plus minus marker expected in line " + source.getLine() + ".");
					}
					int start1 = Integer.parseInt(plusMinusMatcher.group(1));
					int length1 = Integer.parseInt(plusMinusMatcher.group(2));
					int start2 = Integer.parseInt(plusMinusMatcher.group(3));
					int length2 = Integer.parseInt(plusMinusMatcher.group(4));
					
					Chunk chunk = new Chunk(start1, length1, start2, length2);
					int length = length1 + length2;
					while (length > 0) {
						String diffLine = source.next();
						if (diffLine == null) {
							throw error("Expected diff contents in line " + source.getLine() + ".");
						}
						String content = diffLine.substring(1);
						switch (diffLine.charAt(0)) {
							case ' ': {
								chunk.take(content);
								length -= 2;
								break;
							}
							
							case '+': {
								chunk.add(content);
								length--;
								break;
							}
							
							case '-': {
								chunk.delete(content);
								length--;
								break;
							}
							
							default: {
								throw error("Unexpected diff line start in line " + source.getLine() + ": " + diffLine);
							}
						}
					}
					if (length < 0) {
						throw error("Chunk contents does not match chunk header in line " + source.getLine() + ".");
					}
					diff.add(chunk);
				}
				
				return diff;
			}
		}
		
		return null;
	}

	private static RuntimeException error(String message) {
		return new RuntimeException(message);
	}

}

