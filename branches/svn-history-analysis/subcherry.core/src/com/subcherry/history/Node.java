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
import java.util.Collection;
import java.util.List;

import org.tmatesoft.svn.core.SVNNodeKind;

public class Node {

	public enum Kind {
		DIR, FILE, UNKNOWN;

		public static Kind fromSvn(SVNNodeKind kind) {
			if (kind == SVNNodeKind.DIR) {
				return DIR;
			} else if (kind == SVNNodeKind.FILE) {
				return FILE;
			} else {
				return UNKNOWN;
			}
		}
	}

	public static final long SINCE_EVER = 0;

	public static final long FIRST = 1;

	public static final long HEAD = Long.MAX_VALUE;

	private final Kind _kind;

	private final String _path;

	private final List<Change> _changes = new ArrayList<>();

	private final long _revMin;

	private long _revMax;

	/**
	 * The Node with the same path that existed before this one.
	 */
	private Node _before;

	/**
	 * The node with the same path that replaced this one later in time.
	 */
	private Node _later;

	private Node _copyNode;

	private long _copyRevision;

	public Node(Kind kind, String path, long revMin, long revMax) {
		_kind = kind;
		_path = path;
		_revMin = revMin;
		_revMax = revMax;
	}

	public Kind getKind() {
		return _kind;
	}

	public String getPath() {
		return _path;
	}

	public List<Change> getChanges() {
		return getChangesUpTo(HEAD);
	}

	public List<Change> getChangesUpTo(long revision) {
		ArrayList<Change> result = new ArrayList<>();
		addChangesUpTo(result, revision);
		return result;
	}

	private void addChangesUpTo(Collection<Change> result, long revision) {
		if (_copyNode != null) {
			_copyNode.addChangesUpTo(result, _copyRevision);
		}
	
		for (Change change : _changes) {
			if (change.getRevision() <= revision) {
				result.add(change);
			}
		}
	}

	public long getRevMin() {
		return _revMin;
	}

	public boolean isAlive() {
		return _revMax == HEAD;
	}

	public long getRevMax() {
		return _revMax;
	}

	public void setRevMax(long revMax) {
		_revMax = revMax;
	}

	public Node getBefore() {
		return _before;
	}

	public void setBefore(Node before) {
		_before = before;
		_before.setLater(this);
	}

	public Node getLater() {
		return _later;
	}

	private void setLater(Node later) {
		_later = later;
	}

	public Node getCopyNode() {
		return _copyNode;
	}

	public long getCopyRevision() {
		return _copyRevision;
	}

	public void setCopyFrom(Node copyNode, long copyRevision) {
		assert copyNode != null : "Empty copy from.";
		_copyNode = copyNode;
		_copyRevision = copyRevision;
	}

	public void modify(Change change) {
		assert isAlive();
		addChange(change);
	}

	public void delete(Change change) {
		assert isAlive() : "Delete of deleted node.";
		setRevMax(change.getRevision() - 1);
		addChange(change);
	}

	private void addChange(Change change) {
		_changes.add(change);
	}

	@Override
	public String toString() {
		return _path + " [" + _revMin + ", " + (isAlive() ? "HEAD" : _revMax) + "]";
	}

}