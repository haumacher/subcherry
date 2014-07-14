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
package com.subcherry.utils;

import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;

import com.subcherry.history.ChangeType;

public class Path {

	private String _pathName;

	private String _branch;

	private String _module;

	private String _resource;

	private SVNLogEntryPath _pathEntry;

	private Path _copyPath;

	public Path(String pathName, String branch, String module, String resource) {
		_pathName = pathName;
		_branch = branch;
		_module = module;
		_resource = resource;
	}

	public Path(Path orig, String resource) {
		_pathEntry = orig.getPathEntry();
		_copyPath = orig.getCopyPath();

		_pathName = orig.getBranch() + resource;
		_branch = orig.getBranch();
		_module = PathParser.getModule(resource);
		_resource = resource;
	}

	public String getPath() {
		return _pathName;
	}

	public String getBranch() {
		return _branch;
	}

	public String getModule() {
		return _module;
	}

	public String getResource() {
		return _resource;
	}

	void setPathEntry(SVNLogEntryPath pathEntry) {
		_pathEntry = pathEntry;
	}

	public SVNLogEntryPath getPathEntry() {
		return _pathEntry;
	}

	public Path getCopyPath() {
		return _copyPath;
	}

	void setCopyPath(Path copyPath) {
		_copyPath = copyPath;
	}

	public long getCopyRevision() {
		return _pathEntry.getCopyRevision();
	}

	public ChangeType getType() {
		return ChangeType.fromSvn(_pathEntry.getType());
	}

	@Override
	public String toString() {
		return getPath();
	}

	public boolean isDir() {
		return getKind() == SVNNodeKind.DIR;
	}

	public boolean isFile() {
		return getKind() == SVNNodeKind.FILE;
	}

	public SVNNodeKind getKind() {
		return getPathEntry().getKind();
	}

}
