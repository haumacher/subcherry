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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import junit.framework.TestCase;

import com.subcherry.merge.properties.PropertiesEditor;
import com.subcherry.merge.properties.PropertiesEditor.Property;

/**
 * Test case for {@link PropertiesEditor}.
 * 
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 * @version $Revision$ $Author$ $Date$
 */
public class TestPropertiesEditor extends TestCase {

	static final String ISO_8859_1 = "ISO-8859-1";

	public void testSimple() throws IOException {
		assertLoadStore("01-no-whitespace.properties");
	}

	public void testEmptyLine() throws IOException {
		assertLoadStore("02-empty-line.properties");
	}

	public void testComments() throws IOException {
		PropertiesEditor editor = assertLoadStore("03-comments.properties");

		assertEquals(
			quote(Arrays.asList(
				"# comment\r\n",
				"a.foo = a\r\n",
				"\r\n",
				"# other comment\r\n",
				"b.bar = b\r\n",
				"\r\n",
				"c.bar = c\r\n",
				"   \r\n",
				"d.bar = d\r\n")),
			quote(sources(editor)));
	}

	private String quote(List<String> lines) {
		StringBuilder buffer = new StringBuilder();
		for (String line : lines) {
			buffer.append("|");
			buffer.append(line.replace("\r", "\\r").replace("\n", "\\n"));
		}
		return buffer.toString();
	}

	public void testComplex() throws IOException {
		assertLoadStore("04-complex.properties");
	}

	private List<String> sources(PropertiesEditor editor) {
		ArrayList<String> result = new ArrayList<String>();
		for (Property property : editor.getProperties()) {
			result.add(property.getSource());
		}
		return result;
	}

	private PropertiesEditor assertLoadStore(String name) throws FileNotFoundException, IOException {
		File testFile = new File("test/fixtures/TestPropertiesEditor", name);
		PropertiesEditor editor = loadEditor(testFile);

		File outFile = new File(FileTestUtil.tmp(), "TestPropertiesEditor-" + name);
		FileOutputStream out = new FileOutputStream(outFile);
		try {
			editor.store(out);
		} finally {
			out.close();
		}

		String expected = FileTestUtil.loadISO_8859_1(testFile);
		String actual = FileTestUtil.loadISO_8859_1(outFile);

		assertEquals(expected, actual);

		Properties expectedProperties = loadProperties(testFile);
		assertEquals(expectedProperties, editor.asMap());

		return editor;
	}

	private Properties loadProperties(File testFile) throws IOException {
		Properties result = new Properties();
		FileInputStream in = new FileInputStream(testFile);
		try {
			result.load(in);
		} finally {
			in.close();
		}
		return result;
	}

	private PropertiesEditor loadEditor(File testFile) throws FileNotFoundException, IOException {
		PropertiesEditor editor = new PropertiesEditor();
		FileInputStream in = new FileInputStream(testFile);
		try {
			editor.load(in);
		} finally {
			in.close();
		}
		return editor;
	}

}
