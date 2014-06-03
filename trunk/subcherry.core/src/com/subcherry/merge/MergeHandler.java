package com.subcherry.merge;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

import com.subcherry.AdditionalRevision;
import com.subcherry.Configuration;
import com.subcherry.utils.Log;
import com.subcherry.utils.Utils;


/**
 * @version $Revision$ $Author$ $Date$
 */
public class MergeHandler extends Handler {

	private final Set<String> _modules;

	public MergeHandler(Configuration config, Set<String> modules) {
		super(config);
		_modules = modules;
	}

	public Merge parseMerge(SVNLogEntry logEntry) throws SVNException {
		return new Merge(logEntry.getRevision(), buildResources(logEntry));
	}

	private Collection<MergeResource> buildResources(SVNLogEntry logEntry) throws SVNException {
		AdditionalRevision additionalInfo = _config.getAdditionalRevisions().get(logEntry.getRevision());
		Set<String> includePaths;
		if (additionalInfo != null) {
			includePaths = additionalInfo.getIncludePaths();
		} else {
			includePaths = null;
		}
		
		MergeResourceBuilder builder;
		if (includePaths == null) {
			builder = new CompleteChangeSetBuilder(_modules);
		} else {
			builder = new PartialChangeSetBuilder(includePaths);
		}
		return createMergeResources(logEntry, builder);
	}

	private Collection<MergeResource> createMergeResources(SVNLogEntry logEntry, MergeResourceBuilder builder)
			throws SVNException {
		Map<String, MergeResource> resourcesByName = new HashMap<String, MergeResource>();
		Map<String, SVNLogEntryPath> changedPaths = logEntry.getChangedPaths();
		for (String changedPath : changedPaths.keySet()) {
			int moduleStartIndex = getModuleStartIndex(changedPath);
	
			if (moduleStartIndex < 0) {
				Log.warning("Path does not match the branch pattern: " + changedPath);
				continue;
			}
	
			String resourceName = builder.getResourceName(changedPath, moduleStartIndex);
			if (resourceName == null) {
				continue;
			}
	
			if (!resourcesByName.containsKey(resourceName)) {
				String branch = changedPath.substring(0, moduleStartIndex);
				String urlPrefix = _config.getSvnURL() + branch;
	
				resourcesByName.put(resourceName, new MergeResource(_config, logEntry.getRevision(), resourceName,
					urlPrefix, false));
			}
		}
	
		return resourcesByName.values();
	}

	interface MergeResourceBuilder {

		String getResourceName(String changedPath, int moduleStartIndex);

	}

	static class CompleteChangeSetBuilder implements MergeResourceBuilder {

		private Set<String> _modules;

		public CompleteChangeSetBuilder(Set<String> modules) {
			_modules = modules;
		}

		@Override
		public String getResourceName(String changedPath, int moduleStartIndex) {
			String moduleName = getModuleName(changedPath, moduleStartIndex);
			if (!_modules.contains(moduleName)) {
				return null;
			}
			return moduleName;
		}

		private String getModuleName(String changedPath, int moduleStartIndex) {
			int moduleEndIndex = changedPath.indexOf(Utils.SVN_SERVER_PATH_SEPARATOR, moduleStartIndex);
			if (moduleEndIndex < 0) {
				moduleEndIndex = changedPath.length();
			}
			return changedPath.substring(moduleStartIndex, moduleEndIndex);
		}

	}

	static class PartialChangeSetBuilder implements MergeResourceBuilder {

		private Set<String> _includePaths;

		public PartialChangeSetBuilder(Set<String> includePaths) {
			_includePaths = includePaths;
		}

		@Override
		public String getResourceName(String changedPath, int moduleStartIndex) {
			String modulePath = changedPath.substring(moduleStartIndex);
			if (!_includePaths.contains(modulePath)) {
				// Skip path.
				return null;
			}
			return modulePath;
		}

	}

}
