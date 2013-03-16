package com.subcherry;

import java.util.ArrayList;
import java.util.List;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public abstract class SVNLogEntryMatcher implements ISVNLogEntryHandler {
	
	private final class AllEntries extends SVNLogEntryMatcher {
		@Override
		public boolean matches(SVNLogEntry logEntry) {
			return true;
		}
	}

	private List<SVNLogEntry> _entries = new ArrayList<SVNLogEntry>();
	
	protected abstract boolean matches(SVNLogEntry logEntry);

	public List<SVNLogEntry> getEntries() {
		return _entries;
	}
	
	@Override
	public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
		if (matches(logEntry)) {
			_entries.add(logEntry);
		}
	}

	public void forward(ISVNLogEntryHandler mergeCommitHandler) throws SVNException {
		for (SVNLogEntry entry : getEntries()) {
			mergeCommitHandler.handleLogEntry(entry);
		}
	}
}

