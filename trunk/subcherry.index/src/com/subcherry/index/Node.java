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
package com.subcherry.index;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Node {

	public static final long HEAD = Long.MAX_VALUE;

	public static final String COLUMNS = "`id`,`rev_min`,`rev_max`,`parent`,`predecessor`,`predecessor_type`,`name`,`path`,`node_type`";

	private final long id;

	private final long revMin;

	private long revMax;

	private final long parentId;

	private final long predecessorId;

	private final int predecessorType;

	private final String name;

	private final String path;

	private final NodeKind nodeKind;

	private Map<String, Node> _children;

	private Node _parent;

	public Node(long id, long revMin, long revMax, long parentId,
			long predecessorId,
			int predecessorType, String name, String path, NodeKind nodeKind) {
		this.id = id;
		this.revMin = revMin;
		this.revMax = revMax;
		this.parentId = parentId;
		this.predecessorId = predecessorId;
		this.predecessorType = predecessorType;
		this.name = name;
		this.path = path;
		this.nodeKind = nodeKind;
	}

	public long getId() {
		return id;
	}

	public long getRevMin() {
		return revMin;
	}

	public long getRevMax() {
		return revMax;
	}

	public void setRevMax(long revMax) {
		this.revMax = revMax;
	}

	public long getParentId() {
		return parentId;
	}

	public long getPredecessorId() {
		return predecessorId;
	}

	public int getPredecessorType() {
		return predecessorType;
	}

	public String getName() {
		return name;
	}

	public String getPath() {
		return path;
	}

	public NodeKind getNodeKind() {
		return nodeKind;
	}

	public Collection<Node> getChildren() {
		if (_children == null) {
			return Collections.emptyList();
		}
		return _children.values();
	}

	public void addChild(Node node) {
		if (_children == null) {
			_children = new HashMap<>();
		}
		Node clash = _children.put(node.getName(), node);
		if (clash != null) {
			throw new RuntimeException("Duplicate child '" + node.getName()
					+ "' in '" + getPath() + "'");
		}
	}

	public void removeChild(Node node) {
		Node removed = _children.remove(node.getName());
		if (removed != node) {
			throw new RuntimeException("Tried to remove node `"
					+ node.getName() + "` that was not a child of `"
					+ getPath() + ".");
		}
	}

	public Node getParent() {
		return _parent;
	}

	public void setParent(Node parent) {
		_parent = parent;
	}

	@Override
	public String toString() {
		return path;
	}
}
