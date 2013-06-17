package com.subcherry.commit;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.tmatesoft.svn.core.SVNLogEntry;

import com.subcherry.Configuration;
import com.subcherry.merge.Handler;
import com.subcherry.utils.ArrayUtil;
import com.subcherry.utils.Log;
import com.subcherry.utils.Utils;

/**
 * @version $Revision$ $Author$ $Date$
 */
public class CommitHandler extends Handler {
	
	static final String TRUNK = "trunk";
	static final String ORIGINAL = "{original}";
	static final String BACKPORT = "{Backport}";
	
	private final Set<String> _modules;

	final MessageRewriter _messageRewriter;

	public CommitHandler(Configuration config, Set<String> modules, MessageRewriter messageRewriter) {
		super(config);
		
		_modules = modules;
		_messageRewriter = messageRewriter;
	}

	public Commit parseCommit(SVNLogEntry logEntry) {
		Set<String> changedPaths = logEntry.getChangedPaths().keySet();
		Set<File> touchedModules = getTouchedModules(changedPaths);
		File[] affectedPaths = getAffectedPaths(changedPaths);
		String commitMessage = _messageRewriter.resolvePortMessage(logEntry);
		return new Commit(touchedModules, commitMessage, affectedPaths);
	}

	private Set<File> getTouchedModules(Set<String> changedPaths) {
		File workspaceRoot = _config.getWorkspaceRoot();
		HashSet<File> files = new HashSet<File>();
		for (String path : changedPaths) {
			int moduleNameIndex = getModuleStartIndex(path);
			String moduleName = getModuleName(moduleNameIndex, path);
			if (_modules.contains(moduleName)) {
				files.add(new File(workspaceRoot, moduleName));
			} else {
				Log.warning("Skipping change in module '" + moduleName + "' (not in relevant modules '" + _modules + "'): " + path);
			}
		}
		return files;
	}

	private String getModuleName(int moduleNameIndex, String path) {
		if (moduleNameIndex < 0) {
			return null;
		}
		int endIndex = path.indexOf(Utils.SVN_SERVER_PATH_SEPARATOR, moduleNameIndex);
		if (endIndex < 0) {
			endIndex = path.length();
		}
		String moduleName = path.substring(moduleNameIndex, endIndex);
		return moduleName;
	}

	private File[] getAffectedPaths(Set<String> changedPaths) {
		File workspaceRoot = _config.getWorkspaceRoot();
		ArrayList<File> files = new ArrayList<File>();
		for (String path : changedPaths) {
			int moduleNameIndex = getModuleStartIndex(path);
			String moduleName = getModuleName(moduleNameIndex, path);
			if (_modules.contains(moduleName)) {
				files.add(new File(workspaceRoot, path.substring(moduleNameIndex)));
			}
		}
		return files.toArray(ArrayUtil.EMPTY_FILE_ARRAY);
	}

}
