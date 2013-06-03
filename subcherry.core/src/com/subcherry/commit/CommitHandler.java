package com.subcherry.commit;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	
	private static final String TRUNK = "trunk";
	private static final String ORIGINAL = "{original}";
	private static final String BACKPORT = "{Backport}";
	
	private final Set<String> _modules;

	public CommitHandler(Configuration config, Set<String> modules) {
		super(config);
		
		_modules = modules;
	}

	public Commit parseCommit(SVNLogEntry logEntry) {
		Set<String> changedPaths = logEntry.getChangedPaths().keySet();
		Set<File> touchedModules = getTouchedModules(changedPaths);
		File[] affectedPaths = getAffectedPaths(changedPaths);
		String commitMessage = resolvePortMessage(logEntry);
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

	private String resolvePortMessage(SVNLogEntry logEntry) {
		String logEntryMessage = logEntry.getMessage();
		if (ORIGINAL.equals(_config.getPortMessage())) {
			return logEntryMessage; 
		}
		if (BACKPORT.equals(_config.getPortMessage())) {
			return backPortMessage(logEntryMessage); 
		}
		
		Utils.TicketMessage message = new Utils.TicketMessage(logEntryMessage);

		StringBuilder newMesssage = new StringBuilder();
		newMesssage.append("Ticket #");
		newMesssage.append(message.ticketNumber);
		newMesssage.append(": ");

		if (_config.getRebase()) {
			if (message.isHotfix()) {
				addHotfix(newMesssage);
			} else if (message.isPreview()) {
				addPreview(newMesssage);
			} else if (message.isPort()) {
				addPort(newMesssage);
			}
		}
		else if (_config.getPreview()) {
			addPreview(newMesssage);
		} 
		else {
			if (!_config.getRevert()) {
				addPort(newMesssage);
			}
		}

		if (message.apiChange != null) {
			newMesssage.append("API change: ");
		}
		
		if (_config.getRevert()) {
			newMesssage.append("Reverted ");
		}
		newMesssage.append("[");
		newMesssage.append(logEntry.getRevision());
		newMesssage.append("]:");
		
		newMesssage.append(message.originalMessage);
		return newMesssage.toString();
	}

	private void addHotfix(StringBuilder newMesssage) {
		newMesssage.append("Hotfix for ");
		newMesssage.append(getBranchName(_config.getTargetBranch()));
		newMesssage.append(": ");
	}

	private void addPort(StringBuilder newMesssage) {
		newMesssage.append("Ported to ");
		newMesssage.append(getBranchName(_config.getTargetBranch()));
		newMesssage.append(" from ");
		newMesssage.append(getBranchName(_config.getSourceBranch()));
		newMesssage.append(": ");
	}

	private void addPreview(StringBuilder newMesssage) {
		newMesssage.append("Preview on ");
		newMesssage.append(getBranchName(_config.getTargetBranch()));
		newMesssage.append(": ");
	}

	private String backPortMessage(String logEntryMessage) {
		String branchName = getBranchName(_config.getSourceBranch());
		Pattern pattern = Pattern.compile("Ticket #(\\d+): On " + branchName + ": (.*)", Pattern.DOTALL);
		Matcher matcher = pattern.matcher(logEntryMessage);
		if (!matcher.matches()) {
			throw new IllegalStateException();
		}
		String ticketNumber = matcher.group(1);
		String originalMessage = matcher.group(2);
		
		StringBuilder backportMessage = new StringBuilder("Ticket #");
		backportMessage.append(ticketNumber);
		backportMessage.append(": ");
		
		String targetBranch = getBranchName(_config.getTargetBranch());
		if (!isTrunk(targetBranch)) {
			backportMessage.append("On ");
			backportMessage.append(targetBranch);
			backportMessage.append(": ");
		}
		backportMessage.append(originalMessage);
		return backportMessage.toString();
	}

	private boolean isTrunk(String branchName) {
		return branchName.endsWith(TRUNK);
	}

	private String getBranchName(String branch) {
		Pattern trunkPattern = Pattern.compile("^/?(" + TRUNK +")(?:/([^/]+))$");
		Matcher trunkMatcher = trunkPattern.matcher(branch);
		if (trunkMatcher.matches()) {
			String category = trunkMatcher.group(2);
			return (category != null ? category + "_" : "") + TRUNK;
		}
		int index = branch.lastIndexOf(Utils.SVN_SERVER_PATH_SEPARATOR);
		if (index == -1) {
			return branch;
		} else {
			return branch.substring(index +1);
		}
	}

}
