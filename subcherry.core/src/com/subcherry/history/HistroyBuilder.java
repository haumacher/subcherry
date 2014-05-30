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
import java.util.Collections;
import java.util.List;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

/**
 * {@link ISVNLogEntryHandler} that creates a consolidated history of all nodes that are present in
 * the current view of a repository.
 * 
 * <p>
 * The {@link HistroyBuilder} is feed with a SVN log starting with the current version (for which
 * the history view should be built) back to some former revision. During this scan, it records
 * history for all paths consolidating potential moves. In the generated {@link #getHistory()}, all
 * changes are mapped to current paths.
 * </p>
 * 
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 * @version $Revision$ $Author$ $Date$
 */
public class HistroyBuilder implements ISVNLogEntryHandler {

	final static class Replacement implements Comparable<Replacement> {

		private final String _prefix;

		private final String _substitution;

		public Replacement(String prefix, String substitution) {
			_prefix = prefix;
			_substitution = substitution;
		}

		@Override
		public int compareTo(Replacement other) {
			int selfLength = getPrefix().length();
			int otherLength = other.getPrefix().length();

			if (selfLength > otherLength) {
				// Higher prority.
				return -1;
			} else if (selfLength < otherLength) {
				return 1;
			} else {
				return _prefix.compareTo(other._prefix);
			}
		}

		public String getPrefix() {
			return _prefix;
		}

		public String getSubstitution() {
			return _substitution;
		}

		public boolean matches(String path) {
			return path.startsWith(_prefix);
		}

		public String substitute(String path) {
			if (_substitution != null) {
				return _substitution + path.substring(getPrefix().length());
			} else {
				return null;
			}
		}
	}

	private List<Replacement> _replacements = new ArrayList<>();

	private History _history = new History();

	public History getHistory() {
		return _history;
	}

	@Override
	public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
		Change change = createChange(logEntry);

		List<Replacement> rewriters = null;
		for (SVNLogEntryPath pathEntry : logEntry.getChangedPaths().values()) {
			String origPath = pathEntry.getPath();

			char changeType = pathEntry.getType();
			switch (changeType) {
				case SVNLogEntryPath.TYPE_REPLACED: {
					rewriters = delete(rewriters, origPath);
					break;
				}
				case SVNLogEntryPath.TYPE_ADDED: {
					rewriters = removeRewriters(rewriters, origPath);
					break;
				}
			}

			String path = rewrite(origPath);
			if (path != null) {
				if (changeType == SVNLogEntryPath.TYPE_DELETED) {
					rewriters = delete(rewriters, origPath);
				} else {
					if (isMoveTarget(logEntry, pathEntry)) {
						String copyPath = pathEntry.getCopyPath();
						rewriters = addRewriter(rewriters, copyPath, path);
					}

					Node node = _history.mkNode(path);
					node.addChange(change);
				}
			}
		}
		if (rewriters != null) {
			_replacements = rewriters;
		}
	}

	private Change createChange(SVNLogEntry logEntry) {
		Change change =
			_history.createChange(logEntry.getRevision(), logEntry.getAuthor(), logEntry.getDate(),
				logEntry.getMessage());
		return change;
	}

	private String rewrite(String path) {
		for (int n = 0, cnt = _replacements.size(); n < cnt; n++) {
			Replacement replacement = _replacements.get(n);
			if (replacement.matches(path)) {
				return replacement.substitute(path);
			}
		}
		return path;
	}

	private boolean isMoveTarget(SVNLogEntry commit, SVNLogEntryPath path) {
		String copyPath = path.getCopyPath();
		if (copyPath != null) {
			SVNLogEntryPath copiedEntry = commit.getChangedPaths().get(copyPath);
			if (copiedEntry != null) {
				if (copiedEntry.getType() == SVNLogEntryPath.TYPE_DELETED) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The <code>from</code> path was renamed to <code>to</code> path.
	 * 
	 * <p>
	 * Consider the following copy:
	 * </p>
	 * 
	 * <pre>
	 * revision r: /branches/stable <- /branches/unstable
	 * </pre>
	 * 
	 * <p>
	 * All history before (revisions x < r) for sub-paths of <code>/branches/unstable</code> must
	 * afterwards be re-written to <code>/branches/stable</code>. E.g. a change of
	 * <code>/branches/unstable/some-file</code> must be interpreted as being part of the history of
	 * file <code>/branches/stable/some-file</code>.
	 * </p>
	 * 
	 * <p>
	 * Some time before, a package rename has happened on <code>/branches/unstable</code>:
	 * </p>
	 * 
	 * <pre>
	 * revision r - t1: /branches/unstable/new-package <- /branches/unstable/old-package
	 * </pre>
	 * 
	 * <p>
	 * In revisions <code>x < r - t1</code>, changes to files
	 * <code>/branches/unstable/old-package/moved-file</code> must be interpreted as being part of
	 * the history of the current file <code>/branches/stable/new-package/moved-file</code>.
	 * </p>
	 * 
	 * @param fromPath
	 *        The path that was deleted.
	 * @param toPath
	 *        The new path that was created with the contents of the first path.
	 * @return Copy of {@link #_replacements}, if none are given.
	 */
	private List<Replacement> addRewriter(List<Replacement> replacements, String fromPath, String toPath) {
		replacements = allocate(replacements);

		Replacement replacement = new Replacement(fromPath, toPath);
		int index = Collections.binarySearch(replacements, replacement);
		if (index >= 0) {
			Replacement clash = replacements.set(index, replacement);
			if (clash.getSubstitution() != null) {
				if (replacement.getSubstitution() == null) {
					replacements.set(index, clash);
				} else {
					if (!clash.getSubstitution().equals(replacement.getSubstitution())) {
						throw new AssertionError("Identity move '" + fromPath + "' -> '"
							+ replacement.getSubstitution()
							+ "' vs. '" + clash.getSubstitution() + "'.");
					}
				}
			}
		} else {
			replacements.add(insertionPoint(index), replacement);
		}
		return replacements;
	}

	private List<Replacement> allocate(List<Replacement> replacements) {
		if (replacements == null) {
			replacements = new ArrayList<>(_replacements);
		}
		return replacements;
	}

	/**
	 * Drop all rewriters that target paths that have the given path as prefix.
	 * 
	 * @return Copy of {@link #_replacements}, if none are given.
	 */
	private List<Replacement> removeRewriters(List<Replacement> replacements, String path) {
		replacements = allocate(replacements);
		int newPos = 0;
		for (int n = 0, cnt = replacements.size(); n < cnt; n++) {
			Replacement replacement = replacements.get(n);
			if (replacement.getPrefix().startsWith(path)) {
				// Remove by ignoring.
			} else {
				replacements.set(newPos++, replacement);
			}
		}

		for (int n = replacements.size() - 1; n >= newPos; n--) {
			replacements.remove(n);
		}
		return replacements;
	}

	private static int insertionPoint(int index) {
		int insertionPoint = -index - 1;
		return insertionPoint;
	}

	/**
	 * The given path must be considered as being deleted in the future.
	 * 
	 * <p>
	 * All changes on this path must be ignored, when happening before the deletion.
	 * </p>
	 */
	private List<Replacement> delete(List<Replacement> replacements, String path) {
		return addRewriter(replacements, path, null);
	}

}
