/*
 * TimeCollect records time you spent on your development work.
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

import org.tmatesoft.svn.core.SVNLogEntry;

import com.subcherry.commit.Commit;

public class CommitSet {

	private final Commit _commit;

	public CommitSet(SVNLogEntry logEntry, Commit commit) {
		_commit = commit;
	}

	public Commit getCommit() {
		return _commit;
	}

	public Commit getCommit(long joinedRevision) {
		if (getCommit().getLogEntry().getRevision() == joinedRevision) {
			return getCommit();
		}
		return null;
	}

	public void print(PrintStream out) {
		System.out.println(getCommit().getDescription());
	}

}
