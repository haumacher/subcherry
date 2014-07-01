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
package test.com.subcherry.util;

import junit.framework.TestCase;

import com.subcherry.util.VirtualFS;

/**
 * Test case for {@link VirtualFS}.
 * 
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 * @version $Revision$ $Author$ $Date$
 */
public class TestVirtualFS extends TestCase {

	public void testRemove() {
		VirtualFS fs = new VirtualFS();
		fs.delete("foo/bar");

		assertTrue(fs.exists("foo"));
		assertTrue(fs.exists("bar"));
		assertTrue(fs.exists("foo/bazz"));

		assertFalse(fs.exists("foo/bar"));
		assertFalse(fs.exists("foo/bar/bazz"));
	}

	public void testRemoveInAdded() {
		VirtualFS fs = new VirtualFS();
		fs.add("foo/bar");
		fs.delete("foo/bar/bazz");

		assertTrue(fs.exists("foo"));
		assertTrue(fs.exists("bar"));
		assertTrue(fs.exists("foo/bazz"));

		assertTrue(fs.exists("foo/bar"));
		assertFalse(fs.exists("foo/bar/bazz"));
	}

	public void testRemoveAdded() {
		VirtualFS fs = new VirtualFS();
		fs.add("foo/bar/bazz");
		fs.add("foo/xxx");
		fs.delete("foo/bar");

		assertTrue(fs.exists("foo"));
		assertTrue(fs.exists("bar"));
		assertTrue(fs.exists("foo/bazz"));

		assertFalse(fs.exists("foo/bar"));
		assertFalse(fs.exists("foo/bar/bazz"));
		assertTrue(fs.exists("foo/xxx"));

		fs.clear();
		assertTrue(fs.exists("foo/bar"));
		assertTrue(fs.exists("foo/bar/bazz"));
	}

}
