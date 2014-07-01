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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNLogEntry;

import com.subcherry.history.Node.Kind;
import com.subcherry.utils.Utils;

public class DependencyBuilder {

	public static class Dependency {

		private Change _change;

		/**
		 * @see #getRequiredChanges()
		 */
		private final Map<Change, Set<Node>> _requiredChanges = new HashMap<>();

		public Dependency(Change change) {
			_change = change;
		}

		/**
		 * The {@link Change} whose dependencies are described by this instance.
		 */
		public Change getChange() {
			return _change;
		}

		/**
		 * Adds required {@link Change}s for {@link #getChange()} on the given node.
		 * 
		 * @param requiredChanges
		 *        The {@link Change}s that are requirements for {@link #getChange()}.
		 * @param onNode
		 *        The {@link Node} on which a conflict potentially will occur, if the given changes
		 *        are not applied before {@link #getChange()}.
		 */
		public void add(Collection<Change> requiredChanges, Node onNode) {
			for (Change requiredChange : requiredChanges) {
				Set<Node> nodes = _requiredChanges.get(requiredChange);
				if (nodes == null) {
					nodes = new HashSet<>();
					_requiredChanges.put(requiredChange, nodes);
				}
				nodes.add(onNode);
			}
		}

		/**
		 * Required {@link Change}s for {@link #getChange()} mapped to the {@link Node}s where
		 * conflicts are expected, if the requirements are not fulfilled (the dependencies are not
		 * applied before {@link #getChange()} is applied).
		 */
		public Map<Change, Set<Node>> getRequiredChanges() {
			return _requiredChanges;
		}

	}

	private final String _sourceBranch;

	private final String _targetBranch;

	private Map<Change, Dependency> _dependencies = new HashMap<>();

	private Set<String> _modules;

	public DependencyBuilder(String sourceBranch, String targetBranch, Set<String> modules) {
		_sourceBranch = sourceBranch;
		_targetBranch = targetBranch;
		_modules = modules;
	}

	public void analyzeConflicts(History history, List<SVNLogEntry> mergeLog) {
		history.expandContents(_sourceBranch);
		history.expandContents(_targetBranch);

		Set<Change> targetChanges = new HashSet<>();
		for (Node targetNode : fileNodes(history.getNodes(_targetBranch))) {
			targetChanges.addAll(targetNode.getChanges());
		}

		Set<String> alreadyPortedTicketIds = new HashSet<>();
		for (Change change : targetChanges) {
			String id = Utils.getTicketId(change.getMessage());
			if (id != null) {
				alreadyPortedTicketIds.add(id);
			}
		}

		Map<Long, Change> mergedChanges = new HashMap<>();
		for (SVNLogEntry logEntry : mergeLog) {
			Change change = history.getChange(logEntry.getRevision());
			mergedChanges.put(change.getRevision(), change);

			alreadyPortedTicketIds.remove(Utils.getTicketId(change.getMessage()));
		}

		int sourcePrefixLength = _sourceBranch.length() + 1;
		for (Node sourceNode : fileNodes(history.getNodes(_sourceBranch))) {
			String path = sourceNode.getPath();

			if (_modules != null) {
				int moduleEndIndex = path.indexOf('/', sourcePrefixLength);
				if (moduleEndIndex < 0) {
					moduleEndIndex = path.length();
				}
				String module = path.substring(sourcePrefixLength, moduleEndIndex);
				if (!_modules.contains(module)) {
					continue;
				}
			}

			String targetPath = _targetBranch + path.substring(_sourceBranch.length());
			Node targetNode = history.getCurrentNode(sourceNode.getKind(), targetPath);

			Map<String, Change> targetNodeChanges;
			if (targetNode == null) {
				// Does not exist in target branch, all change sets that are not ported are in
				// conflict.
				targetNodeChanges = Collections.emptyMap();
			} else {
				targetNodeChanges = new HashMap<>();
				for (Change change : targetNode.getChanges()) {
					String key = change.getKey();
					if (key != null) {
						targetNodeChanges.put(key, change);
					}
				}
			}

			List<Change> merges = new ArrayList<>();
			List<Change> dependencies = new ArrayList<>();
			for (Change sourceChange : sourceNode.getChanges()) {
				if (mergedChanges.keySet().contains(sourceChange.getRevision())) {
					merges.add(sourceChange);
					if (!dependencies.isEmpty()) {
						Dependency dependency = mkDependency(sourceChange);
						dependency.add(copy(dependencies), sourceNode);
					}
				} else {
					// The change is not being merged.
					if (!targetNodeChanges.containsKey(sourceChange.getKey())) {
						// There is no equivalent change on the target node.

						// Add the change to the dependency list. If there are following changes
						// being merged, those are marked as depending on this change.
						dependencies.add(sourceChange);

						if (alreadyPortedTicketIds.contains(Utils.getTicketId(sourceChange.getMessage()))) {
							// The change is expected to occur on the target node, but does not.
							// This might be the case, because it only affects functionality that
							// is first introduced with the changes being currently merged.

							// All changes before the missing change are potentially require
							// re-applying the missing change.
							for (Change merged : merges) {
								mkDependency(merged).add(Collections.singleton(sourceChange), sourceNode);
							}
						}
					}
				}
			}
		}
	}

	private Iterable<Node> fileNodes(Collection<Node> nodes) {
		ArrayList<Node> result = new ArrayList<>();
		for (Node node : nodes) {
			if (node.getKind() != Kind.FILE) {
				// Conflicts are only relevant on files, not directories. On directories, only
				// property conflicts may occur, which are most probable "conflicts" in
				// svn:mergeinfo, which is always resolved automatically.
				continue;
			}

			result.add(node);
		}
		return result;
	}

	public Map<Change, Dependency> getDependencies() {
		return _dependencies;
	}

	private static <T> List<T> copy(Collection<T> values) {
		return new ArrayList<>(values);
	}

	private Dependency mkDependency(Change change) {
		Dependency result = _dependencies.get(change);
		if (result == null) {
			result = new Dependency(change);
			_dependencies.put(change, result);
		}
		return result;
	}

}
