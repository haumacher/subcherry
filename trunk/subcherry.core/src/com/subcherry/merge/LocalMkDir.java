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
package com.subcherry.merge;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNOperation;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
 * {@link SVNOperation} that locally creates a directory in a working copy and schedules if for
 * addition.
 * 
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 * @version $Revision$ $Author$ $Date$
 */
public class LocalMkDir extends SvnOperation<Void> {

	/**
	 * Creates a {@link LocalMkDir}.
	 *
	 * @param factory
	 *        See {@link #getOperationFactory()}.
	 */
	public LocalMkDir(SvnOperationFactory factory) {
		super(factory);
	}

	@Override
	public Void run() throws SVNException {
		for (SvnTarget target : getTargets()) {
			File dir = target.getFile();
			if (dir == null) {
				throw new IllegalArgumentException("Only local targets supported: " + target);
			}
			createAndAdd(dir);
		}
		return null;
	}

	private void createAndAdd(File dir) throws SVNException {
		if (dir.exists()) {
			if (dir.isDirectory()) {
				// Silently ignore.
			} else {
				// Report conflict.
				SVNConflictDescription conflict =
					new SVNTreeConflictDescription(dir, SVNNodeKind.DIR, SVNConflictAction.ADD,
						SVNConflictReason.OBSTRUCTED, SVNOperation.MERGE,
						null, null);
				getOptions().getConflictResolver().handleConflict(conflict);
			}
			return;
		}

		File parent = dir.getParentFile();
		createAndAdd(parent);

		boolean success = dir.mkdir();
		if (!success) {
			// Report problem.

		}

		SvnScheduleForAddition add = getOperationFactory().createScheduleForAddition();
		add.setSingleTarget(SvnTarget.fromFile(dir));
		add.run();
	}
}