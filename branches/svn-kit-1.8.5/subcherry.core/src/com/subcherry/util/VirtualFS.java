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
package com.subcherry.util;

import java.util.Iterator;
import java.util.TreeSet;

/**
 * Keeps track of adds and removes to the file system when creating a merge.
 * 
 * <p>
 * The {@link VirtualFS} is able to predict the existence of a resource during creation of the merge
 * operations before the merges are actually performed.
 * </p>
 * 
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 * @version $Revision$ $Author$ $Date$
 */
public class VirtualFS {

	private final TreeSet<String> _deletedPaths = new TreeSet<>();

	private final TreeSet<String> _addedPaths = new TreeSet<>();

	public VirtualFS() {
		super();
	}

	public void clear() {
		_deletedPaths.clear();
		_addedPaths.clear();
	}

	public void delete(String resource) {
		removeDescendents(_deletedPaths, resource);
		removeDescendentsOrSelf(_addedPaths, resource);

		_deletedPaths.add(resource);
	}

	public void add(String resource) {
		removeDescendents(_addedPaths, resource);
		removeDescendentsOrSelf(_deletedPaths, resource);

		_addedPaths.add(resource);
	}

	public boolean exists(String resource) {
		while (true) {
			if (_deletedPaths.contains(resource)) {
				return false;
			}
			if (_addedPaths.contains(resource)) {
				// Some ancetor of the resource has been added.
				return true;
			}

			int dirSeparatorIndex = resource.lastIndexOf('/');
			if (dirSeparatorIndex < 0) {
				// There is not evidence that the path has been deleted.
				return true;
			}

			resource = resource.substring(0, dirSeparatorIndex);
		}
	}

	private static void removeDescendentsOrSelf(TreeSet<String> paths, String resource) {
		paths.remove(resource);
		removeDescendents(paths, resource);
	}

	private static void removeDescendents(TreeSet<String> paths, String resource) {
		String dirPrefix = prefix(resource);
		Iterator<String> descendents = paths.tailSet(dirPrefix, false).iterator();
		while (descendents.hasNext()) {
			String potentialDescendent = descendents.next();
			if (potentialDescendent.startsWith(dirPrefix)) {
				descendents.remove();
			} else {
				break;
			}
		}
	}

	private static String prefix(String resource) {
		if (resource.endsWith("/")) {
			return resource;
		} else {
			return resource + '/';
		}
	}

}
