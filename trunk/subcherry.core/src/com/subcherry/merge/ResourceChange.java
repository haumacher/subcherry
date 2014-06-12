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

import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

/**
 * Description of the change of a single resource (file or directory) in a certain change set.
 * 
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 * @version $Revision$ $Author$ $Date$
 */
public class ResourceChange {

	private final SVNLogEntry _changeSet;

	private final SVNLogEntryPath _change;

	public ResourceChange(SVNLogEntry logEntry, SVNLogEntryPath pathEntry) {
		_changeSet = logEntry;
		_change = pathEntry;
	}

	/**
	 * The change set.
	 */
	public SVNLogEntry getChangeSet() {
		return _changeSet;
	}

	/**
	 * The change within the {@link #getChangeSet()}.
	 */
	public SVNLogEntryPath getChange() {
		return _change;
	}

}
