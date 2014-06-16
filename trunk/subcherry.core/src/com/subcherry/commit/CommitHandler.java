package com.subcherry.commit;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

import com.subcherry.Configuration;
import com.subcherry.merge.Handler;
import com.subcherry.utils.ArrayUtil;
import com.subcherry.utils.Log;
import com.subcherry.utils.Path;
import com.subcherry.utils.PathParser;
import com.subcherry.utils.Utils;
import com.subcherry.utils.Utils.TicketMessage;

/**
 * @version $Revision$ $Author$ $Date$
 */
public class CommitHandler extends Handler {
	
	static final String TRUNK = "trunk";
	static final String ORIGINAL = "{original}";
	static final String BACKPORT = "{Backport}";
	
	private final Set<String> _modules;

	final MessageRewriter _messageRewriter;

	private final PathParser _paths;

	public CommitHandler(Configuration config, PathParser paths, Set<String> modules, MessageRewriter messageRewriter) {
		super(config);
		
		_paths = paths;
		_modules = modules;
		_messageRewriter = messageRewriter;
	}

	public Commit parseCommit(SVNLogEntry logEntry) {
		Set<File> touchedModules = getTouchedModules(logEntry);
		File[] affectedPaths = inWorkspace(logEntry);
		TicketMessage ticketMessage = new TicketMessage(logEntry.getRevision(), logEntry.getMessage(), _messageRewriter);
		return new Commit(logEntry, touchedModules, ticketMessage, affectedPaths);
	}

	private Set<File> getTouchedModules(SVNLogEntry logEntry) {
		File workspaceRoot = _config.getWorkspaceRoot();
		HashSet<File> files = new HashSet<File>();
		for (SVNLogEntryPath pathEntry : logEntry.getChangedPaths().values()) {
			Path path = _paths.parsePath(pathEntry);
			String module = path.getModule();
			if (_modules.contains(module)) {
				files.add(new File(workspaceRoot, module));
			} else {
				Log.warning("Skipping change in module '" + module + "' (not in relevant modules '" + _modules + "'): "
					+ path);
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

	public File[] inWorkspace(SVNLogEntry logEntry) {
		File workspaceRoot = _config.getWorkspaceRoot();
		ArrayList<File> files = new ArrayList<File>();
		for (SVNLogEntryPath pathEntry : logEntry.getChangedPaths().values()) {
			Path path = _paths.parsePath(pathEntry);
			if (_modules.contains(path.getModule())) {
				files.add(new File(workspaceRoot, path.getResource()));
			}
		}
		return files.toArray(ArrayUtil.EMPTY_FILE_ARRAY);
	}

}
