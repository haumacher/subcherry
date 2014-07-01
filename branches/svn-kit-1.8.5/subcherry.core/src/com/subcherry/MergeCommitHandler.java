package com.subcherry;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;

import com.subcherry.commit.Commit;
import com.subcherry.commit.CommitContext;
import com.subcherry.commit.RevisionRewriter;
import com.subcherry.merge.Merge;
import com.subcherry.merge.MergeContext;
import com.subcherry.merge.MergeHandler;
import com.subcherry.utils.Log;
import com.subcherry.utils.Utils;

/**
 * @version $Revision$ $Author$ $Date$
 */
public class MergeCommitHandler {

	private enum InputResult {
		SKIP,
		REAPPLY,
		CONTINUE,
		;

		public static AssertionError noSuchInputResult(InputResult result) {
			throw new AssertionError("No such " + InputResult.class.getName() + ": " + result);
		}
	}

	private static class UpdateableRevisionRewriter implements RevisionRewriter {

		private Map<Long, Long> _buffer = new HashMap<Long, Long>();

		@Override
		public long rewrite(long oldRevision) {
			Long newRevision = _buffer.get(oldRevision);
			if (newRevision == null) {
				return oldRevision;
			}
			return newRevision;
		}

		public void add(long originalRevision, long newRevision) {
			if (newRevision <= 0) {
				return;
			}
			_buffer.put(originalRevision, newRevision);
		}
	}

	private final MergeHandler _mergeHandler;
	private final CommitContext _commitContext;
	private final SVNDiffClient _diffClient;
	private final MergeContext _mergeContext;
	private final boolean _autoCommit;

	private List<CommitSet> _commitSets;

	private final Set<Long> joinedRevisions = new HashSet<Long>();

	private int _totalRevs;

	private int _doneRevs;
	
	private final UpdateableRevisionRewriter _revisionRewrite = new UpdateableRevisionRewriter();

	private Set<Long> _stopOnRevisions;

	private Configuration _config;

	public MergeCommitHandler(MergeHandler mergeHandler, SVNClientManager clientManager, Configuration config) {
		this._mergeHandler = mergeHandler;
		_config = config;
		this._diffClient = clientManager.getDiffClient();
		if (config.getNoCommit()) {
			_commitContext = null;
		} else {
			_commitContext = new CommitContext(clientManager.getUpdateClient(), clientManager.getCommitClient());
		}
		this._mergeContext = new MergeContext(_diffClient);
		_autoCommit = config.getAutoCommit();
		_stopOnRevisions = new HashSet<Long>(Arrays.asList(config.getStopOnRevisions()));
	}

	public void run(List<CommitSet> commitSets) throws SVNException {
		_commitSets = commitSets;
		_totalRevs = getTotalRevs(commitSets);

		for (int n = 0, cnt = _commitSets.size(); n < cnt; n++) {
			CommitSet commitSet = _commitSets.get(n);
			
			for (Commit commit : commitSet.getCommits()) {
				long revision = commit.getRevision();
				if (joinedRevisions.contains(revision)) {
					continue;
				}

				try {
					Restart.setRevision(revision);
				} catch (IOException ex) {
					Log.info("Unable to store restart revision");
				}

				merge(commit, commit.getLogEntry());
			}
		}
	}

	private int getTotalRevs(List<CommitSet> commitSets) {
		int result = 0;
		for (CommitSet commitSet : commitSets) {
			result += commitSet.getCommits().size();
		}
		return result;
	}
	
	public void merge(Commit commit, SVNLogEntry logEntry) throws SVNException {
		_doneRevs++;

		Merge merge = _mergeHandler.parseMerge(logEntry);
		if (merge.isEmpty()) {
			Log.info("Skipping '" + merge.getRevision() + "' (no relevant modules touched).");
			return;
		}
		
		boolean commitAproval = _autoCommit && !_stopOnRevisions.contains(merge.getRevision());
		
		System.out.println("Revision " + logEntry.getRevision() + " (" + _doneRevs + " of " + _totalRevs + "): "
			+ encode(logEntry.getMessage()));

		merge:
		while (true) {
			Map<File, List<SVNConflictDescription>> conflicts = merge.run(_mergeContext);
			commit.addTouchedResources(merge.getTouchedResources());

			if (!conflicts.isEmpty()) {
				log(conflicts);
				if (_config.getNoCommit() && _config.getAutoSkipConflicts()) {
					System.out.println("Automatically skipping conflicts in [" + logEntry.getRevision() + "].");
					return;
				}
				InputResult result = queryCommit(commit, "commit");
				switch (result) {
					case SKIP:
						return;
					case CONTINUE:
						break;
					case REAPPLY:
						continue merge;
					default:
						throw InputResult.noSuchInputResult(result);
				}
				commitAproval = true;
			}

			if (_commitContext == null) {
				Log.info("Revision '" + logEntry.getRevision() + "' applied but not committed.");
			} else {
				if (!commitAproval) {
					InputResult result = queryCommit(commit, "commit");
					switch (result) {
						case SKIP:
							return;
						case CONTINUE:
							break;
						case REAPPLY:
							continue merge;
						default:
							throw InputResult.noSuchInputResult(result);
					}
				}
				while (true) {
					try {
						Log.info("Execute:" + commit);
						SVNCommitInfo commitInfo = commit.run(_commitContext);
						Log.info("Revision '" + logEntry.getRevision() + "' merged and commited as '"
							+ commitInfo.getNewRevision() + "'.");

						_revisionRewrite.add(logEntry.getRevision(), commitInfo.getNewRevision());
						break;
					} catch (SVNException ex) {
						System.out.println("Commit failed: " + ex.getLocalizedMessage());

						InputResult result = queryCommit(commit, "retry");
						switch (result) {
							case SKIP:
								return;
							case CONTINUE:
								break;
							case REAPPLY:
								continue merge;
							default:
								throw InputResult.noSuchInputResult(result);
						}
					}
				}

			}
			break;
		}
		
	}

	private InputResult queryCommit(Commit commit, String continueCommand) throws SVNException {
		String skipCommand = "skip";
		String stopCommand = "stop";
		String apiCommand = "api";
		String reapplyCommand = "re-apply";
		String excludeCommand = "exclude: ";
		String includeCommand = "include: ";
		String commitCommand = "commit: ";
		String joinCommand = "join: ";
		try {
			while (true) {
				System.out.println(commitCommand + encode(commit.getCommitMessage()));
				System.out.println("Enter " + 
				"'" + continueCommand  + "' to commit, " + 
				"'" + commitCommand + "<message>' to commit with another message, " + 
				"'" + apiCommand + "', to add \"API change\" to the message, " + 
				"'" + reapplyCommand + "', to re-apply current change set, " +  
				"'" + excludeCommand + "<path to exclude>', to exclude a certain path from commit, " + 
				"'" + includeCommand + "<path to include>', to include a certain path from commit, " + 
				"'" + skipCommand + "' to skip this revision or " + 
				"'" + joinCommand + "<revision>' to join a following revision with the current commit or " + 
				"'" + stopCommand + "' to stop the tool!");
				String input = Utils.SYSTEM_IN.readLine();
				if (input.startsWith(commitCommand)) {
					commit.setCommitMessage(decode(input.substring(commitCommand.length())));
					return InputResult.CONTINUE;
				}
				if (input.startsWith(excludeCommand)) {
					String excludedPath = input.substring(excludeCommand.length());
					
					boolean removed = commit.excludeResource(excludedPath);
					if (!removed) {
						System.err.println("Not in pathes being committed '" + excludedPath + ":");
						for (String path : commit.getTouchedResourcesList()) {
							System.err.println("   " + path);
						}
					}
					continue;
				}
				if (input.startsWith(includeCommand)) {
					String includePath = input.substring(includeCommand.length());
					
					boolean added = commit.includeResource(includePath);
					if (!added) {
						System.err.println("Path already contained: " + includePath);
					}
					continue;
				}
				if (apiCommand.equals(input)) {
					String portedTo = "Ported to [^ ]+(?: from [^:]+)?: ";
					String previewOn = "Preview on [^ ]+: ";
					String mergeReason = "(?:" + portedTo + '|' + previewOn + ')';
					Pattern apiChangeInsertPattern =
						Pattern.compile("^(Ticket #\\d+: " + mergeReason + ")(.*)$", Pattern.DOTALL);
					Matcher matcher = apiChangeInsertPattern.matcher(commit.getCommitMessage());
					if (! matcher.matches()) {
						System.err.println("Message could not be parsed to insert API change flag.");
						continue;
					}
					commit.setCommitMessage(matcher.group(1) + "API change: " + matcher.group(2));
					return InputResult.CONTINUE;
				}
				if (continueCommand.equals(input)) {
					return InputResult.CONTINUE;
				}
				if (skipCommand.equals(input)) {
					return InputResult.SKIP;
				}
				if (reapplyCommand.equals(input)) {
					return InputResult.REAPPLY;
				}
				if (input.startsWith(joinCommand)) {
					long joinedRevision = Long.parseLong(input.substring(joinCommand.length()));
					Commit joinedCommit = getEntry(joinedRevision);
					if (joinedCommit == null) {
						System.err.println("Revision [" + joinedRevision + "] is not part of this merge.");
						continue;
					}
					joinedRevisions.add(joinedRevision);
					
					commit.join(joinedCommit);
					
					merge(commit, joinedCommit.getLogEntry());
					return InputResult.SKIP;
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

	private Commit getEntry(long joinedRevision) {
		for (CommitSet commitSet : _commitSets) {
			Commit commit = commitSet.getCommit(joinedRevision);
			if (commit != null) {
				return commit;
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
	
	public void log(Map<File, List<SVNConflictDescription>> conflicts) {
		StringBuilder message = new StringBuilder("Merge has conflicts in files:");
		for (Entry<File, List<SVNConflictDescription>> entry : conflicts.entrySet()) {
			String absolutePath = entry.getKey().getAbsolutePath();
			message.append('\n');
			message.append(toResource(absolutePath));
			message.append(':');
			message.append(' ');
			boolean first = true;
			for (SVNConflictDescription conflict : entry.getValue()) {
				if (first) {
					first = false;
				} else {
					message.append(',');
				}
				if (conflict.isPropertyConflict()) {
					message.append("property");
				}
				if (conflict.isTextConflict()) {
					message.append("text");
				}
				if (conflict.isTreeConflict()) {
					SVNTreeConflictDescription treeConflict = (SVNTreeConflictDescription) conflict;
					message.append("tree (");
					message.append(treeConflict.getConflictAction());
					message.append(" but locally ");
					message.append(treeConflict.getConflictReason());
					message.append(")");
				}
			}

		}
		System.out.println(message.toString());
	}

	private String toResource(String wcPath) {
		return Utils.toResource(_config.getWorkspaceRoot(), wcPath);
	}

	public RevisionRewriter getRevisionRewriter() {
		return _revisionRewrite;
	}

}
