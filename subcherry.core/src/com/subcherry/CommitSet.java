/*
 * SubCherry - Cherry Picking with Trac and Subversion
 * Copyright (C) 2013 Bernhard Haumacher and others
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
package com.subcherry;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import com.subcherry.repository.core.LogEntry;
import com.subcherry.commit.Commit;

public class CommitSet {

	private final List<Commit> _commits;

	public CommitSet(LogEntry logEntry, Commit commit) {
		_commits = new ArrayList<Commit>(4);
		add(commit);
	}

	public List<Commit> getCommits() {
		return _commits;
	}

	public Commit getLeadCommit() {
		return _commits.get(0);
	}

	public Commit getCommit(long joinedRevision) {
		for (Commit commit : _commits) {
			if (commit.getLogEntry().getRevision() == joinedRevision) {
				return commit;
			}
		}
		return null;
	}

	public void print(PrintStream out) {
		for (Commit commit : _commits) {
			out.println(commit.getDescription());
		}
	}

	public boolean isEmpty() {
		return getCommits().isEmpty();
	}

	public void add(Commit commit) {
		_commits.add(commit);
	}

}
