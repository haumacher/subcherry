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

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class History {

	private Map<Long, Change> _changesByRevision = new HashMap<>();

	private Map<String, Node> _nodesByPath = new HashMap<>();

	Change createChange(long revision, String author, Date date, String message) {
		Change result = new Change(revision, author, date, message);
		Change clash = _changesByRevision.put(result.getRevision(), result);
		assert clash == null : "Duplicate revision '" + result.getRevision() + "'.";
		return result;
	}

	Node mkNode(String path) {
		Node node = getNode(path);
		if (node != null) {
			return node;
		}

		Node newNode = new Node(path);
		_nodesByPath.put(path, newNode);
		return newNode;
	}

	public Node getNode(String path) {
		return _nodesByPath.get(path);
	}

	public Map<Long, Change> getChangesByRevision() {
		return _changesByRevision;
	}

	public Map<String, Node> getNodesByPath() {
		return _nodesByPath;
	}

	public Collection<Node> getTouchedNodes() {
		return _nodesByPath.values();
	}

	public Change getChange(long revision) {
		Change result = _changesByRevision.get(revision);
		if (result == null) {
			throw new IllegalArgumentException("No such revision '" + revision + "'.");
		}
		return result;
	}
}