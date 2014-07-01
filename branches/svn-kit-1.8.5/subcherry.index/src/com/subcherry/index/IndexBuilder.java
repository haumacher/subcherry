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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;

import com.subcherry.history.ChangeType;

public class IndexBuilder implements Closeable, Flushable {

	private static final Comparator<SVNLogEntryPath> PATH_ORDER = new Comparator<SVNLogEntryPath>() {
		@Override
		public int compare(SVNLogEntryPath p1, SVNLogEntryPath p2) {
			return p1.getPath().compareTo(p2.getPath());
		}
	};

	private final Map<String, Node> _currentNodeByPath = new HashMap<String, Node>();

	private final Connection _connection;

	private final Batch _insertRevision;

	private long _nextId = 1;

	private PathLookup _pathLookup;

	private final ChildrenLookup _childrenLookup;

	private final InsertNode _insertNode;

	private final OutdateNode _outdateNode;

	private final InsertContent _insertContent;

	private final OutdateContent _outdateContent;

	private Set<Node> _modifications = new HashSet<>();

	private Set<Node> _creations = new HashSet<>();

	private Set<Node> _deletions = new HashSet<>();

	public IndexBuilder(Connection connection) throws SQLException {
		_connection = connection;
		_pathLookup = new PathLookup(connection);
		_childrenLookup = new ChildrenLookup(connection);
		_insertRevision = new Batch(connection,
				"INSERT INTO `revision` (`id`, `author`, `date`, `message`) VALUES (?, ?, ?, ?)");
		_outdateNode = new OutdateNode(connection);
		_insertNode = new InsertNode(connection);

		_outdateContent = new OutdateContent(connection);
		_insertContent = new InsertContent(connection);
	}

	public void insert(SVNLogEntry logEntry) {
		try {
			insertRevision(logEntry);

			ArrayList<SVNLogEntryPath> paths = new ArrayList<SVNLogEntryPath>(
					logEntry.getChangedPaths().values());
			// Sort by path to ensure that a directory creation is found
			// before a content file creation.
			Collections.sort(paths, PATH_ORDER);
			long revision = logEntry.getRevision();
			for (SVNLogEntryPath path : paths) {
				insertPath(revision, path);
			}

			commit(revision);
			flush();
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private void insertPath(long revision, SVNLogEntryPath path)
			throws SQLException {
		dumpPath(path);

		ChangeType changeType = ChangeType.fromSvn(path.getType());
		NodeKind nodeKind = kindId(path.getKind());

		String fullPath = path.getPath();

		if (changeType == ChangeType.MODIFIED) {
			Node node = getCurrentNode(fullPath);
			markModified(node);
		} else if (changeType == ChangeType.DELETED) {
			Node node = getCurrentNode(fullPath);
			markDeleted(node);
		} else {
			if (changeType == ChangeType.REPLACED) {
				Node node = getCurrentNode(fullPath);
				markDeleted(node);
			}

			long id = createId();

			int nameSeparatorIndex = fullPath.lastIndexOf('/');
			String parentPath = fullPath.substring(0, nameSeparatorIndex);
			String name = fullPath.substring(nameSeparatorIndex + 1);

			Node parentNode;
			long parentId;
			if (nameSeparatorIndex > 0) {
				parentNode = getCurrentNode(parentPath);
				parentId = parentNode.getId();
			} else {
				parentNode = null;
				parentId = 0;
			}

			long predecessorId;
			int predecessorType;

			String copyPath = path.getCopyPath();
			if (copyPath != null) {
				predecessorId = getId(copyPath, path.getCopyRevision());
				predecessorType = 0;
			} else {
				predecessorId = 0;
				predecessorType = 0;
			}

			Node node = new Node(id, revision, Node.HEAD, parentId,
					predecessorId, predecessorType, name, fullPath, nodeKind);
			markInserted(parentNode, node);

			if (predecessorId != 0) {
				if (nodeKind == NodeKind.DIR) {
					// Copy children.
					deepCopy(revision, predecessorType, predecessorId, path.getCopyRevision(), node);
				} else {
					// TODO: Copy content.
				}
			} else {
				if (nodeKind == NodeKind.FILE) {
					_insertContent.insert(id, revision, Node.HEAD);
				}
			}
		}
	}

	private Node dropNode(String path) {
		Node cachedNode = _currentNodeByPath.remove(path);
		ensureFound(path, cachedNode);
		return cachedNode;
	}

	protected void markInserted(Node parent, Node node) {
		assert node.getRevMax() == Node.HEAD;

		if (parent != null) {
			parent.addChild(node);
		}
		node.setParent(parent);

		_creations.add(node);

		String path = node.getPath();
		Node previousNode = _currentNodeByPath.put(path, node);

		assert previousNode == null : "Duplicate node '" + node.getPath()
				+ "'.";
	}

	private void markDeleted(Node node) {
		if (node.getNodeKind() == NodeKind.DIR) {
			for (Node child : copy(getCurrentChildren(node))) {
				markDeleted(child);
			}
		}

		if (!_creations.remove(node)) {
			_deletions.add(node);
		}
		_modifications.remove(node);

		Node parent = node.getParent();
		if (parent != null) {
			parent.removeChild(node);
			node.setParent(null);
		}

		dropNode(node.getPath());
	}

	private <T> List<T> copy(Collection<T> values) {
		return new ArrayList<>(values);
	}

	private void markModified(Node node) {
		_modifications.add(node);
	}

	private Collection<Node> getCurrentChildren(Node node) {
		return node.getChildren();
	}

	protected Node getCurrentNode(String path) {
		Node cachedNode = _currentNodeByPath.get(path);
		ensureFound(path, cachedNode);
		return cachedNode;
	}

	private void ensureFound(String path, Node cachedNode) {
		if (cachedNode == null) {
			throw new RuntimeException("Node '" + path + "' not found in current cache.");
		}
	}

	protected void deepCopy(long revision, int predecessorType,
			long predecessorId, long predecessorRevision, Node node)
			throws SQLException {
		List<Node> predecessorChildren = _childrenLookup.getChildren(predecessorId, predecessorRevision);
		for (Node predecessorChild : predecessorChildren) {
			String newPath = node.getPath() + '/' + predecessorChild.getName();
			Node newChild = new Node(createId(), revision, Node.HEAD,
					node.getId(), predecessorChild.getId(), predecessorType,
					predecessorChild.getName(), newPath,
					predecessorChild.getNodeKind());
			markInserted(node, newChild);

			if (predecessorChild.getNodeKind() == NodeKind.DIR) {
				deepCopy(revision, predecessorType, predecessorChild.getId(),
						predecessorRevision,
						newChild);
			}
		}
	}

	protected long createId() {
		long id = _nextId++;
		return id;
	}

	private long getId(String path, long revision) throws SQLException {
		Node currentNode = _currentNodeByPath.get(path);
		if (currentNode != null) {
			if (currentNode.getRevMin() <= revision) {
				return currentNode.getId();
			}
		}

		return _pathLookup.lookupPath(path, revision);
	}

	private static NodeKind kindId(SVNNodeKind kind) {
		if (kind == SVNNodeKind.FILE) {
			return NodeKind.FILE;
		}
		if (kind == SVNNodeKind.DIR) {
			return NodeKind.DIR;
		}
		if (kind == SVNNodeKind.NONE) {
			return NodeKind.NONE;
		}
		if (kind == SVNNodeKind.UNKNOWN) {
			return NodeKind.UNKNOWN;
		}
		return NodeKind.UNKNOWN;
	}

	private void dumpPath(SVNLogEntryPath path) {
		System.out.print("   " + path.getKind().toString() + " "
				+ path.getType() + " " + path.getPath());
		if (path.getCopyPath() != null) {
			System.out.print(" <- " + path.getCopyPath() + " ("
					+ path.getCopyRevision() + ")");
		}
		System.out.println();
	}

	private void insertRevision(SVNLogEntry logEntry) throws SQLException {
		dumpRevision(logEntry);

		int index = 1;
		_insertRevision.setLong(index++, logEntry.getRevision());
		_insertRevision.setString(index++, logEntry.getAuthor());
		_insertRevision.setTimestamp(index++, new Timestamp(logEntry.getDate()
				.getTime()));
		_insertRevision.setString(index++, logEntry.getMessage());
		_insertRevision.addBatch();
	}

	private void dumpRevision(SVNLogEntry logEntry) {
		System.out.println(logEntry.getRevision() + ": "
				+ SvnSync.quote(logEntry.getMessage()) + " ("
				+ logEntry.getAuthor() + ")");
	}

	private void commit(long revision) throws SQLException {
		long revMax = revision - 1;
		for (Node node : _deletions) {
			if (node.getNodeKind() == NodeKind.FILE) {
				_outdateContent.outdate(node.getId(), revMax);
			}
			_outdateNode.outdate(node.getId(), revMax);
		}

		for (Node node : _creations) {
			_insertNode.insert(node);
			if (node.getNodeKind() == NodeKind.FILE) {
				// _insertContent.insert(node.getId(), revision, Node.HEAD);
			}
		}

		for (Node node : _modifications) {
			if (node.getNodeKind() == NodeKind.FILE) {
				long id = node.getId();
				_outdateContent.outdate(id, revMax);
				_insertContent.insert(id, revision, Node.HEAD);
			} else {
				// Directory modifications (property changes) are not yet
				// indexed.
			}
		}

		_deletions.clear();
		_creations.clear();
		_modifications.clear();
	}

	@Override
	public void flush() throws IOException {
		_insertRevision.flush();
		_outdateContent.flush();
		_outdateNode.flush();
		_insertNode.flush();
		_insertContent.flush();

		try {
			_connection.commit();
		} catch (SQLException ex) {
			SvnSync.toIOException(ex);
		}
	}

	@Override
	public void close() throws IOException {
		try {
			_insertNode.close();
		} finally {
			try {
				_insertRevision.close();
			} finally {
				try {
					_outdateNode.close();
				} finally {
					try {
						_outdateContent.close();
					} finally {
						// Further closing.
					}
				}
			}
		}
	}
}