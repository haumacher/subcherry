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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc2.ISvnOperationHandler;
import org.tmatesoft.svn.core.wc2.SvnOperation;

/**
 * {@link ISvnOperationHandler} that searches for conflicts on touched files after an operation has
 * completed successfully.
 * 
 * @author dbusche@gmail.com
 * @version $Revision$ $Author$ $Date$
 */
public final class MergeConflictCollector implements ISvnOperationHandler {

	private Map<File, List<SVNConflictDescription>> _lazyConflicts = null;

	private final ISvnOperationHandler _delegate;

	private final Iterable<File> _touchedFiles;

	public MergeConflictCollector(ISvnOperationHandler delegate, Iterable<File> touchedFiles) {
		_delegate = delegate;
		_touchedFiles = touchedFiles;
	}

	public Map<File, List<SVNConflictDescription>> getMergeConflicts() {
		if (_lazyConflicts == null) {
			return Collections.emptyMap();
		}
		return _lazyConflicts;
	}

	private void addConflict(File file, List<SVNConflictDescription> conflicts) {
		if (_lazyConflicts == null) {
			_lazyConflicts = new HashMap<>();
		}
		_lazyConflicts.put(file, conflicts);
	}

	public ISvnOperationHandler getDelegate() {
		return _delegate;
	}

	@Override
	public void beforeOperation(SvnOperation<?> operation) throws SVNException {
		if (_delegate != null) {
			_delegate.beforeOperation(operation);
		}
	}

	@Override
	public void afterOperationSuccess(SvnOperation<?> operation) throws SVNException {
		if (_delegate != null) {
			_delegate.afterOperationSuccess(operation);
		}
		ISVNWCDb wcDb = operation.getOperationFactory().getWcContext().getDb();
		for (File file : _touchedFiles) {
			List<SVNConflictDescription> conflicts = wcDb.readConflicts(file);
			if (!conflicts.isEmpty()) {
				addConflict(file, conflicts);
			}
		}
	}

	@Override
	public void afterOperationFailure(SvnOperation<?> operation) {
		if (_delegate != null) {
			_delegate.afterOperationFailure(operation);
		}
	}
}