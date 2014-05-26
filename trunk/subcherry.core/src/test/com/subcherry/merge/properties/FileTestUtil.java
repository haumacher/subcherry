/*
 * TimeCollect records time you spent on your development work.
 * Copyright (C) 2014 Bernhard Haumacher and others
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
package test.com.subcherry.merge.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Utilities for testing with {@link File}s.
 * 
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 * @version $Revision$ $Author$ $Date$
 */
public class FileTestUtil {

	public static File tmp() {
		File tmp = new File("tmp");
		tmp.mkdirs();
		return tmp;
	}

	public static String loadISO_8859_1(File file) throws IOException {
		StringBuilder buffer = new StringBuilder();
	
		char[] chunk = new char[1024];
		InputStreamReader in = new InputStreamReader(new FileInputStream(file), TestPropertiesEditor.ISO_8859_1);
		try {
			int direct;
			while ((direct = in.read(chunk)) >= 0) {
				buffer.append(chunk, 0, direct);
			}
		} finally {
			in.close();
		}
		return buffer.toString();
	}

}
