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
package com.subcherry;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;

public class CombinedLogEntryHandler implements ISVNLogEntryHandler {

	private ISVNLogEntryHandler _handler1;

	private ISVNLogEntryHandler _handler2;

	public CombinedLogEntryHandler(ISVNLogEntryHandler handler1, ISVNLogEntryHandler handler2) {
		_handler1 = handler1;
		_handler2 = handler2;
	}

	@Override
	public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
		_handler1.handleLogEntry(logEntry);
		_handler2.handleLogEntry(logEntry);
	}

}
