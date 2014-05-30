package com.subcherry.merge;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

import com.subcherry.Configuration;
import com.subcherry.utils.Log;
import com.subcherry.utils.Utils;


/**
 * @version $Revision$ $Author$ $Date$
 */
public class MergeHandler extends Handler {

	private final Collection<String> _modules;

	public MergeHandler(Configuration config, Collection<String> modules) {
		super(config);
		_modules = modules;
	}

	public Merge parseMerge(SVNLogEntry logEntry) throws SVNException {
		long revision = logEntry.getRevision();
		Collection<SVNModule> changedModules = getChangedModules(logEntry);
		return new Merge(revision, changedModules, _config.getRevert());
	}

	private Collection<SVNModule> getChangedModules(SVNLogEntry logEntry) throws SVNException {
		Map<String, SVNModule> changedModules = new HashMap<String, SVNModule>();
		Map<String, SVNLogEntryPath> changedPaths = logEntry.getChangedPaths();
		for (String changedPath : changedPaths.keySet()) {
			int moduleStartIndex = getModuleStartIndex(changedPath);
			
			if (moduleStartIndex < 0) {
				Log.warning("Path does not match the branch pattern: " + changedPath);
				continue;
			}
			int moduleEndIndex = changedPath.indexOf(Utils.SVN_SERVER_PATH_SEPARATOR, moduleStartIndex);
			if (moduleEndIndex < 0) {
				moduleEndIndex = changedPath.length();
			}
			String moduleName = changedPath.substring(moduleStartIndex, moduleEndIndex);
			
			String branch = changedPath.substring(0, moduleStartIndex);
			String urlPrefix = _config.getSvnURL() + branch;

			changedModules.put(moduleName, new SVNModule(moduleName, urlPrefix));
		}
		changedModules.keySet().retainAll(_modules);
		return changedModules.values();
	}

}
