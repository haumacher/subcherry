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
package com.subcherry.index;

import org.tmatesoft.svn.core.SVNLogEntryPath;

public enum ChangeType {
	ADDED, MODIFIED, DELETED,

	/**
	 * {@link #DELETED} and {@link #ADDED} in one commit.
	 */
	REPLACED;

	public static ChangeType fromSvn(char type) {
		switch (type) {
			case SVNLogEntryPath.TYPE_ADDED:
				return ADDED;
			case SVNLogEntryPath.TYPE_MODIFIED:
				return MODIFIED;
			case SVNLogEntryPath.TYPE_DELETED:
				return DELETED;
			case SVNLogEntryPath.TYPE_REPLACED:
				return REPLACED;
			default: {
				throw new IllegalArgumentException("Unknown change type '" + type + "'.");
			}
		}
	}

}
