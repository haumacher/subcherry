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
package com.subcherry.history;

import java.util.ArrayList;
import java.util.List;

public class Node {

	private final String _path;

	private final List<Change> _changes = new ArrayList<>();

	public Node(String path) {
		_path = path;
	}

	public void addChange(Change change) {
		_changes.add(change);
	}

	public String getPath() {
		return _path;
	}

	public List<Change> getChanges() {
		return _changes;
	}

}