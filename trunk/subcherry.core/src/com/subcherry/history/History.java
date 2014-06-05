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

import com.subcherry.history.Node.Kind;

public class History {

	private Map<Long, Change> _changesByRevision = new HashMap<>();

	private Map<String, Node> _nodesByPath = new HashMap<>();

	private final long _startRevision;

	public History(long startRevision) {
		_startRevision = startRevision;
	}

	/**
	 * The first revision recorded.
	 */
	public long getStartRevision() {
		return _startRevision;
	}

	public Change createChange(long revision, String author, Date date, String message) {
		Change result = new Change(revision, author, date, message);
		Change clash = _changesByRevision.put(result.getRevision(), result);
		assert clash == null : "Duplicate revision '" + result.getRevision() + "'.";
		return result;
	}

	public void addedNode(Kind kind, String path, Change change, String copyPath, long copyRevision) {
		Node existingNode = getLatestNode(path);
		if (existingNode != null && existingNode.isAlive()) {
			// If not the complete history is read (filtered for some paths), it is possible that a
			// branch create is seen but a corresponding branch delete is missing. This happens,
			// because the branch create is in the direct history of a logged path, but the branch
			// delete is not.
			markDeleted(existingNode, change);
		}

		Node node = createCurrentNode(kind, path, change.getRevision());
		node.modify(change);

		if (copyPath != null) {
			Node copyNode = lookupNode(kind, copyPath, copyRevision);
			if (copyNode == null) {
				if (_startRevision > Node.FIRST) {
					// History was not recorded for that revision, create a phantom node.
					copyNode = mkHistoricNode(kind, copyPath, Node.SINCE_EVER, copyRevision);

					// Extends the live-time of the phantom node to the maximum possible.
					if (copyNode.getLater() == null) {
						copyNode.setRevMax(Node.HEAD);
					} else {
						copyNode.setRevMax(copyNode.getLater().getRevMin() - 1);
					}
				} else {
					throw new AssertionError("Copy node not found: " + copyPath + " in " + copyRevision);
				}
			}
			node.setCopyFrom(copyNode, copyRevision);
		}
	}

	public void modifiedNode(Kind kind, String path, Change change) {
		Node node = mkCurrentNode(kind, path, change.getRevision());
		node.modify(change);
	}

	public void deletedNode(Kind kind, String path, Change change) {
		Node node = mkCurrentNode(kind, path, change.getRevision());
		markDeleted(node, change);
	}

	private void markDeleted(Node node, Change change) {
		node.delete(change);

		String path = node.getPath();
		if (node.getKind() == Kind.DIR) {
			// Delete potential children.
			for (Node child : _nodesByPath.values()) {
				if (child.isAlive() && child.getPath().startsWith(path)) {
					child.delete(change);
				}
			}
		}
	}

	Node mkCurrentNode(Kind kind, String path, long revision) {
		Node node = getCurrentNode(kind, path);
		if (node != null) {
			return node;
		}
		return createCurrentNode(kind, path, revision);
	}

	public Node getCurrentNode(Kind kind, String path) {
		Node node = lookupNode(kind, path, Node.HEAD);
		if (node == null || !node.isAlive()) {
			return null;
		}
		return node;
	}

	private Node lookupNode(Kind kind, String path, long revision) {
		Node node = getLatestNode(path);
		if (node != null) {
			Node inRevision = backToRevision(node, revision);
			if (inRevision != null) {
				return inRevision;
			}
		}
	
		int dirSeparatorIndex = path.lastIndexOf('/');
		if (dirSeparatorIndex < 0) {
			// No parent path, the original path is not found at all.
			return null;
		}
	
		String parentPath = path.substring(0, dirSeparatorIndex);
		Node parentNode = lookupNode(kind, parentPath, revision);
		if (parentNode == null) {
			// No parent path, the original path is not found at all.
			return null;
		}
		
		if (revision == Node.SINCE_EVER) {
			return null;
		}

		if (node != null && inRange(node, parentNode.getRevMin(), parentNode.getRevMax()) != null) {
			// The node has an explicit representative in the range of its parent. Since it was not
			// found above, another implicit representative must not be created.
			return null;
		}

		Node childNode = mkHistoricNode(kind, path, parentNode.getRevMin(), parentNode.getRevMax());
		Node parentCopyNode = parentNode.getCopyNode();
		if (parentCopyNode != null) {
			String namePart = path.substring(dirSeparatorIndex);
			long parentCopyRevision = parentNode.getCopyRevision();
			String copyPath = parentCopyNode.getPath() + namePart;
			Node copyNode = lookupNode(kind, copyPath, parentCopyRevision);

			// Note: With an incomplete history, the copied node may not be found.
			if (copyNode != null) {
				childNode.setCopyFrom(copyNode, parentCopyRevision);
			}
		}
		return childNode;
	}

	private Node backToRevision(Node node, long revision) {
		return inRange(node, revision, revision);
	}

	private Node inRange(Node node, long revMin, long revMax) {
		while (node.getRevMin() > revMax) {
			node = node.getBefore();
			if (node == null) {
				// Requested path is older than all path recorded.
				return null;
			}
		}

		if (node.getRevMax() < revMin) {
			// Requested path is newer than the delete revision of the oldest path found.
			return null;
		}
	
		return node;
	}

	private Node createCurrentNode(Kind kind, String path, long revision) {
		return createNode(kind, path, revision, Node.HEAD);
	}

	private Node mkHistoricNode(Kind kind, String path, long revMin, long revMax) {
		if (revMin == Node.SINCE_EVER) {
			Node phantomNode = lookupNode(kind, path, Node.SINCE_EVER);
			if (phantomNode != null) {
				phantomNode.setRevMax(revMax);
				assert phantomNode.getLater() == null || assertBefore(phantomNode, phantomNode.getLater());
				return phantomNode;
			}
		}
		return createNode(kind, path, revMin, revMax);
	}

	private Node createNode(Kind kind, String path, long revMin, long revMax) {
		Node node = new Node(kind, path, revMin, revMax);
		enterNode(node);
		return node;
	}

	private void enterNode(Node node) throws AssertionError {
		Node latest = _nodesByPath.get(node.getPath());
		if (latest == null) {
			_nodesByPath.put(node.getPath(), node);
			return;
		}

		while (node.getRevMin() < latest.getRevMin()) {
			Node before = latest.getBefore();
			if (before == null) {
				// Insert before latest.
				assert assertBefore(node, latest);

				latest.setBefore(node);
				return;
			}
			latest = before;
		}

		// Note: Fetch info before modifying the relations.
		Node later = latest.getLater();

		assert assertBefore(latest, node);
		if (later != null) {
			assert assertBefore(node, later);
		}

		// Insert after latest.
		node.setBefore(latest);
		if (later == null) {
			_nodesByPath.put(node.getPath(), node);
		} else {
			later.setBefore(node);
		}
	}

	private boolean assertBefore(Node before, Node after) {
		assert before.getRevMax() < after.getRevMin() : "Overlapping histories: " + before + ", " + after;
		return true;
	}

	private Node getLatestNode(String path) {
		return _nodesByPath.get(path);
	}

	public Map<Long, Change> getChangesByRevision() {
		return _changesByRevision;
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