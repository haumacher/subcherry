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
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class LogReader {

	private final SVNLogClient _logClient;

	private final SVNURL _url;

	private SVNRevision _startRevision;

	private SVNRevision _endRevision;

	private SVNRevision _pegRevision;

	private boolean _stopOnCopy;

	private boolean _discoverChangedPaths;

	private long _limit;

	public LogReader(SVNLogClient logClient, SVNURL url) {
		_logClient = logClient;
		_url = url;
	}

	public SVNRevision getStartRevision() {
		return _startRevision;
	}

	public void setStartRevision(SVNRevision startRevision) {
		_startRevision = startRevision;
	}

	public SVNRevision getEndRevision() {
		return _endRevision;
	}

	public void setEndRevision(SVNRevision endRevision) {
		_endRevision = endRevision;
	}

	public SVNRevision getPegRevision() {
		return _pegRevision;
	}

	public void setPegRevision(SVNRevision pegRevision) {
		_pegRevision = pegRevision;
	}

	public boolean isStopOnCopy() {
		return _stopOnCopy;
	}

	public void setStopOnCopy(boolean stopOnCopy) {
		_stopOnCopy = stopOnCopy;
	}

	public boolean isDiscoverChangedPaths() {
		return _discoverChangedPaths;
	}

	public void setDiscoverChangedPaths(boolean discoverChangedPaths) {
		_discoverChangedPaths = discoverChangedPaths;
	}

	public long getLimit() {
		return _limit;
	}

	public void setLimit(long limit) {
		_limit = limit;
	}

	public void readLog(String[] paths, ISVNLogEntryHandler logTarget) throws SVNException {
		_logClient.doLog(_url, paths, _pegRevision, _startRevision, _endRevision, _stopOnCopy, _discoverChangedPaths,
			_limit, logTarget);
	}

}
