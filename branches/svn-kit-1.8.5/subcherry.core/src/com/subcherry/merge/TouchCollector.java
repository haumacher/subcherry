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
import java.util.HashSet;
import java.util.Set;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;

/**
 * {@link ISVNEventHandler} that records all touched files during a SVN operation.
 * 
 * @author dbusche@gmail.com
 * @version $Revision$ $Author$ $Date$
 */
public final class TouchCollector implements ISVNEventHandler {

	private final Set<File> _touchedFiles = new HashSet<File>();
	private final ISVNEventHandler _delegate;

	public TouchCollector(ISVNEventHandler delegate) {
		_delegate = delegate;
	}

	public Set<File> getTouchedFiles() {
		return _touchedFiles;
	}

	public ISVNEventHandler getDelegate() {
		return _delegate;
	}

	@Override
	public void checkCancelled() throws SVNCancelException {
		if (_delegate != null) {
			_delegate.checkCancelled();
		}
		// not canceled by any button or so.
	}

	@Override
	public void handleEvent(SVNEvent event, double progress)
			throws SVNException {
		if (_delegate != null) {
			_delegate.handleEvent(event, progress);
		}
		_touchedFiles.add(event.getFile());
	}
}