package com.subcherry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNDiffClient;

import com.subcherry.commit.Commit;
import com.subcherry.commit.CommitContext;
import com.subcherry.commit.CommitHandler;
import com.subcherry.merge.Merge;
import com.subcherry.merge.MergeContext;
import com.subcherry.merge.MergeHandler;
import com.subcherry.utils.Log;
import com.subcherry.utils.Utils;

/**
 * @version $Revision$ $Author$ $Date$
 */
public class MergeCommitHandler {

	private final MergeHandler _mergeHandler;
	private final CommitHandler _commitHandler;
	private final CommitContext _commitContext;
	private final SVNDiffClient _diffClient;
	private final MergeContext _mergeContext;
	private final boolean _autoCommit;
	private final List<SVNLogEntry> logEntries;
	private final Set<Long> joinedRevisions = new HashSet<Long>();
	
	public MergeCommitHandler(List<SVNLogEntry> logEntries, MergeHandler mergeHandler, CommitHandler commitHandler,
			SVNClientManager clientManager, Configuration config) {
		this.logEntries = logEntries;
		this._mergeHandler = mergeHandler;
		this._diffClient = clientManager.getDiffClient();
		this._commitHandler = commitHandler;
		if (config.getNoCommit()) {
			_commitContext = null;
		} else {
			_commitContext = new CommitContext(clientManager.getUpdateClient(), clientManager.getCommitClient());
		}
		this._mergeContext = new MergeContext(_diffClient, config);
		_autoCommit = config.getAutoCommit();
	}

	public void run() throws SVNException {
		for (int n = 0, cnt = logEntries.size(); n < cnt; n++) {
			SVNLogEntry entry = logEntries.get(n);
			if (joinedRevisions.contains(entry.getRevision())) {
				continue;
			}
			
			try {
				Restart.setRevision(entry.getRevision());
			} catch (IOException ex) {
				Log.info("Unable to store restart revision");
			}
			
			merge(_commitHandler.parseCommit(entry), entry);
		}
	}
	
	public void merge(Commit commit, SVNLogEntry logEntry) throws SVNException {
		Merge merge = _mergeHandler.parseMerge(logEntry);
		if (merge.changedModules.size() == 0) {
			Log.info("Skipping '" + merge.revision + "' (no relevant modules touched).");
			return;
		}
		
		boolean commitAproval = false;
		
		System.out.println("Revision " + logEntry.getRevision() + ": " + encode(logEntry.getMessage()));
		List<SVNConflictDescription> conflicts = merge.run(_mergeContext);
		if (conflicts != Merge.NO_CONFLICTS) {
			log(conflicts);
			boolean skip = queryCommit(commit, "commit");
			if (skip) {
				return;
			}
			commitAproval = true;
		}

		if (_commitContext == null) {
			Log.info("Revision '" + logEntry.getRevision() + "' applied but not committed.");
		} else {
			if (!_autoCommit && !commitAproval) {
				boolean skip = queryCommit(commit, "commit");
				if (skip) {
					return;
				}
			}
			while(true) {
				try {
					Log.info("Execute:" + commit);
					commit.run(_commitContext);
					Log.info("Revision '" + logEntry.getRevision() + "' applied and committed.");
					break;
				} catch(SVNException ex) {
					System.out.println("Commit failed: " + ex.getLocalizedMessage());
					
					boolean skip = queryCommit(commit, "retry");
					if (skip) {
						return;
					}
				}
			}
			
		}
		
	}

	private boolean queryCommit(Commit commit, String continueCommand) throws SVNException {
		String skipCommand = "skip";
		String stopCommand = "stop";
		String apiCommand = "api";
		String excludeCommand = "exclude: ";
		String includeCommand = "include: ";
		String commitCommand = "commit: ";
		String joinCommand = "join: ";
		try {
			while (true) {
				System.out.println(commitCommand + encode(commit.commitMessage));
				System.out.println("Enter " + 
				"'" + continueCommand  + "' to commit, " + 
				"'" + commitCommand + "<message>' to commit with another message, " + 
				"'" + apiCommand + "', to add \"API change\" to the message, " + 
				"'" + excludeCommand + "<path to exclude>', to exclude a certain path from commit, " + 
				"'" + includeCommand + "<path to include>', to include a certain path from commit, " + 
				"'" + skipCommand + "' to skip this revision or " + 
				"'" + joinCommand + "<revision>' to join a following revision with the current commit or " + 
				"'" + stopCommand + "' to stop the tool!");
				String input = Utils.SYSTEM_IN.readLine();
				if (input.startsWith(commitCommand)) {
					commit.commitMessage = decode(input.substring(commitCommand.length()));
					return false;
				}
				if (input.startsWith(excludeCommand)) {
					String excludedPath = input.substring(excludeCommand.length());
					
					List<File> changedPaths = new ArrayList<File>(Arrays.asList(commit.affectedPaths));
					boolean removed = changedPaths.remove(new File(excludedPath));
					if (!removed) {
						System.err.println("Not in pathes being committed '" + excludedPath + ":");
						for (File path : changedPaths) {
							System.err.println("   " + path.getPath());
						}
					} else {
						commit.affectedPaths = changedPaths.toArray(new File[changedPaths.size()]);
					}
					continue;
				}
				if (input.startsWith(includeCommand)) {
					String includePath = input.substring(includeCommand.length());
					
					List<File> changedPaths = new ArrayList<File>(Arrays.asList(commit.affectedPaths));
					if (changedPaths.contains(new File(includePath))) {
						System.err.println("Path already contained: " + includePath);
					} else {
						boolean added = changedPaths.add(new File(includePath));
						assert added: "Could not add path: " + includePath;
						commit.affectedPaths = changedPaths.toArray(new File[changedPaths.size()]);
					}
					continue;
				}
				if (apiCommand.equals(input)) {
					Pattern apiChangeInsertPattern = Pattern.compile("^(Ticket #\\d+: Ported to [^ ]+(?: from [^:]+)?: )(.*)$", Pattern.DOTALL);
					Matcher matcher = apiChangeInsertPattern.matcher(commit.commitMessage);
					if (! matcher.matches()) {
						System.err.println("Message could not be parsed to insert API change flag.");
						continue;
					}
					commit.commitMessage = matcher.group(1) + "API change: " + matcher.group(2);
					return false;
				}
				if (continueCommand.equals(input)) {
					return false;
				}
				if (skipCommand.equals(input)) {
					return true;
				}
				if (input.startsWith(joinCommand)) {
					long joinedRevision = Long.parseLong(input.substring(joinCommand.length()));
					SVNLogEntry joinedEntry = getEntry(joinedRevision);
					if (joinedEntry == null) {
						System.err.println("Revision [" + joinedRevision + "] is not part of this merge.");
						continue;
					}
					joinedRevisions.add(joinedRevision);
					
					Commit joinedCommit = _commitHandler.parseCommit(joinedEntry);
					commit.join(joinedCommit);
					
					merge(commit, joinedEntry);
					return true;
				}
				if (stopCommand.equals(input)) {
					System.out.println("Stopping tool");
					System.exit(0);
				}
				System.err.println("Illegal input.");
			}
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private SVNLogEntry getEntry(long joinedRevision) {
		for (SVNLogEntry entry : logEntries) {
			if (entry.getRevision() == joinedRevision) {
				return entry;
			}
		}
		return null;
	}

	public static String encode(String s) {
		return s.replace("\n", "\\n").replace("\r", "\\r");
	}

	public static String decode(String s) {
		return s.replace("\\n", "\n").replace("\\r", "\r");
	}
	
	public void log(List<SVNConflictDescription> conflicts) {
		StringBuilder message = new StringBuilder("Merge has conflicts in files:");
		for (int index = 0, size = conflicts.size(); index < size; index++) {
			message.append('\n');
			message.append(conflicts.get(index).getPath().getAbsolutePath());
		}
		System.out.println(message.toString());
	}

}