/*
 * SubCherry - Cherry Picking with Trac and Subversion
 * Copyright (C) 2015 Bernhard Haumacher and others
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.subcherry.repository.merge.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

public class PropertiesNormalizer {

	private static final String NL;

	static {
		StringWriter nlBuffer = new StringWriter();
		PrintWriter nlPrinter = new PrintWriter(nlBuffer);
		nlPrinter.println();
		nlPrinter.close();

		NL = nlBuffer.toString();
	}

	public static void normalize(File file) throws IOException {
		Properties properties;

		FileInputStream in = new FileInputStream(file);
		try {
			properties = new Properties();
			properties.load(in);
		} finally {
			in.close();
		}

		store(file, properties);
	}

	public static void store(File file, Properties properties) throws IOException {
		FileOutputStream out = new FileOutputStream(file);
		try {
			store(out, properties);
		} finally {
			out.close();
		}
	}

	public static void store(OutputStream out, Properties properties) throws IOException {
		Charset charset = Charset.forName("ISO_8859-1");
		CharsetEncoder encoder = charset.newEncoder();
		OutputStreamWriter writer = new OutputStreamWriter(out, charset);

		ArrayList<String> keyList = new ArrayList<String>((Set) properties.keySet());
		Collections.sort(keyList);

		for (String key : keyList) {
			writer.write(key);
			writer.write(" = ");
			String translation = properties.getProperty(key);
			int start = 0;
			int limit = translation.length();
			while (start < limit) {
				char ch = translation.charAt(start);
				if (!whiteSpace(ch)) {
					break;
				}
				start++;
			}
			while (limit > start) {
				char ch = translation.charAt(limit - 1);
				if (!whiteSpace(ch)) {
					break;
				}
				limit--;
			}
			for (int n = 0, cnt = translation.length(); n < cnt; n++) {
				char ch = translation.charAt(n);
				switch (ch) {
					case '\\': {
						writer.write("\\\\");
						break;
					}
					case '\r': {
						writer.write("\\r");
						break;
					}
					case '\n': {
						writer.write("\\n");
						break;
					}
					case ' ':
						if (n >= start && n < limit) {
							writer.write(ch);
						} else {
							writer.write("\\ ");
						}
						break;
					case '\t':
						if (n >= start && n < limit) {
							writer.write(ch);
						} else {
							writer.write("\\t");
						}
						break;
					case '\f':
						if (n >= start && n < limit) {
							writer.write(ch);
						} else {
							writer.write("\\f");
						}
						break;
					default: {
						if (encoder.canEncode(ch)) {
							writer.write(ch);
						} else {
							encode(writer, ch);
						}
					}
				}
			}
			writer.write(NL);
		}

		writer.flush();
	}

	private static boolean whiteSpace(char ch) {
		return ch == ' ' || ch == '\t' || ch == '\f';
	}

	private static void encode(OutputStreamWriter writer, char ch) throws IOException {
		writer.write("\\u" + fill(Integer.toHexString(ch).toUpperCase()));
	}

	private static String fill(String hexString) {
		return "0000".substring(hexString.length()) + hexString;
	}

}
