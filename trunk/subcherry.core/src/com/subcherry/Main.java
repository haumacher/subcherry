package com.subcherry;

import static com.subcherry.Globals.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNDiffConflictChoiceStyle;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import com.subcherry.commit.Commit;
import com.subcherry.commit.CommitHandler;
import com.subcherry.commit.MessageRewriter;
import com.subcherry.commit.RevisionRewriter;
import com.subcherry.log.DirCollector;
import com.subcherry.merge.ContentSensitiveMerger;
import com.subcherry.merge.MergeHandler;
import com.subcherry.trac.TracConnection;
import com.subcherry.utils.Log;
import com.subcherry.utils.Utils;

import de.haumacher.common.config.PropertiesUtil;

/**
 * @version $Revision$ $Author$ $Date$
 */
public class Main {

	/**
	 * Indicates that all entries are requested. <b>Note: </b> Can not use
	 * {@link Long#MAX_VALUE} because the server can not handle it (also all
	 * values &gt; {@link Integer#MAX_VALUE}):
	 * 
	 * <pre>
	 * svn: E175002: can not read HTTP status line
	 * svn: E175002: REPORT request failed on '/svn/repo/!svn/bc/110768/trunk/project'
	 * </pre>
	 */
	private static final long NO_LIMIT = 0; // 0 means all

	private static Set<String> _modules;

	public static void main(String[] args) throws IOException, SVNException {
		LoginCredential tracCredentials = PropertiesUtil.load("conf/loginCredentials.properties", "trac.",
				LoginCredential.class);

		doMerge(tracCredentials);
	}

	public static void doMerge(LoginCredential tracCredentials) throws SVNException, IOException {
		SVNRevision startRevision = config().getRevert() ? getEndRevision() : getStartRevision();
		SVNRevision endRevision = config().getRevert() ? getStartRevision() : getEndRevision();
		SVNRevision pegRevision = getPegRevision(startRevision);
		boolean stopOnCopy = false;
		boolean discoverChangedPaths = true;
		long limit = NO_LIMIT;
		SVNClientManager clientManager = newSVNClientManager();
		SVNLogClient logClient = clientManager.getLogClient();
		
		SVNURL sourceBranchUrl = SVNURL.parseURIDecoded(config().getSvnURL() + Utils.SVN_SERVER_PATH_SEPARATOR + config().getSourceBranch());
		SVNURL targetBranchUrl = SVNURL.parseURIDecoded(config().getSvnURL() + Utils.SVN_SERVER_PATH_SEPARATOR + config().getTargetBranch());
		if (config().getDetectCommonModules() || config().getModules().length == 0) {
			_modules = DirCollector.getBranchModules(logClient, config().getModules(), sourceBranchUrl, pegRevision);
		} else {
			_modules = new HashSet<String>(Arrays.asList(config().getModules()));
		}
		_modules.retainAll(getWorkspaceModules());
		Log.info("Merging modules: " + _modules);
		
		TracConnection trac = createTracConnection(tracCredentials);
		PortingTickets portingTickets = new PortingTickets(config(), trac);
		MergeHandler mergeHandler = new MergeHandler(config(), _modules);
		MergeCommitHandler mergeCommitHandler =
			new MergeCommitHandler(mergeHandler, clientManager, config());
		RevisionRewriter revisionRewriter = mergeCommitHandler.getRevisionRewriter();
		MessageRewriter messageRewriter =
			MessageRewriter.createMessageRewriter(config(), portingTickets, revisionRewriter);
		SVNLogEntryMatcher logEntryMatcher = newLogEntryMatcher(trac, portingTickets);
		CommitHandler commitHandler = newCommitHandler(messageRewriter);
		SVNURL url = SVNURL.parseURIDecoded(config().getSvnURL());
		String[] paths = getLogPaths();

		logClient.doLog(url, paths, pegRevision, startRevision, endRevision, stopOnCopy, discoverChangedPaths, limit,
				logEntryMatcher);
		
		List<CommitSet> commitSets = getCommitSets(commitHandler, logEntryMatcher.getEntries());
		if (config().getReorderCommits()) {
			reorderCommits(commitSets);
		}
		for (CommitSet commitSet : commitSets) {
			commitSet.print(System.out);
		}
		Log.info("Start merging " + logEntryMatcher.getEntries().size() + " revisions.");

		mergeCommitHandler.run(commitSets);

		Restart.clear();
	}

	private static String[] getLogPaths() {
		String[] paths = new String[_modules.size() + 1];

		// Add paths for each concrete module: This is done because the module could have been
		// copied to the branch. In this case the changes in the module before copy time are not
		// logged for the whole branch, but for the concrete module.
		int i = 0;
		StringBuilder path = new StringBuilder();
		for (String module : _modules) {
			path.append(config().getSourceBranch());
			if (path.charAt(path.length() - 1) != '/')
				path.append('/');
			path.append(module);
			paths[i++] = path.toString();
			path.setLength(0);
		}

		// Add also whole branch to get changes like deletion or copying of modules which are not
		// logged for the module itself.
		paths[i] = config().getSourceBranch();
		return paths;
	}

	private static void reorderCommits(List<CommitSet> commitSets) {
		HashMap<Long, CommitSet> commitSetByLeadRevision = new HashMap<Long, CommitSet>();
		for (Iterator<CommitSet> setIt = commitSets.iterator(); setIt.hasNext();) {
			CommitSet commitSet = setIt.next();

			for (Iterator<Commit> it = commitSet.getCommits().iterator(); it.hasNext();) {
				Commit commit = it.next();

				long followUpRevision = commit.getFollowUpForRevison();
				if (followUpRevision > 0) {
					CommitSet leadCommitSet = commitSetByLeadRevision.get(followUpRevision);
					if (leadCommitSet != null) {
						leadCommitSet.add(commit);
						it.remove();

						commitSetByLeadRevision.put(commit.getRevision(), leadCommitSet);
					} else {
						Log.warning("Lead commit for follow-up not found: " + commit.getDescription());
					}
				}
			}

			if (!commitSet.isEmpty()) {
				commitSetByLeadRevision.put(commitSet.getCommits().get(0).getRevision(), commitSet);
			} else {
				setIt.remove();
			}
		}
	}

	private static List<CommitSet> getCommitSets(CommitHandler commitHandler, List<SVNLogEntry> logEntries) {
		ArrayList<CommitSet> result = new ArrayList<CommitSet>(logEntries.size());
		for (SVNLogEntry logEntry : logEntries) {
			result.add(new CommitSet(logEntry, commitHandler.parseCommit(logEntry)));
		}
		return result;
	}

	private static SVNRevision getPegRevision(SVNRevision startRevision) {
		return getRevisionOrHead(config().getPegRevision());
	}

	private static Set<String> getWorkspaceModules() {
		File workspaceRoot = config().getWorkspaceRoot();
		if (!workspaceRoot.exists()) {
			throw new RuntimeException("Workspace root '" + workspaceRoot.getAbsolutePath() + "' does not exist.");
		}
		if (!workspaceRoot.isDirectory()) {
			throw new RuntimeException("Workspace root '" + workspaceRoot.getAbsolutePath() + "' is not a directory.");
		}
		File[] workspaceModuleDirs = workspaceRoot.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.isDirectory() && !file.getName().startsWith(".");
			}
		});
		Set<String> workspaceModules = new HashSet<String>();
		for (File moduleDir : workspaceModuleDirs) {
			workspaceModules.add(moduleDir.getName());
		}
		return workspaceModules;
	}

	public static SVNRevision getStartRevision() {
		long storedRevision = Restart.getRevision();
		if (storedRevision != Restart.NO_REVISION_FOUND) {
			return SVNRevision.create(storedRevision);
		} else {
			return getRevisionOrHead(config().getStartRevision());
		}
	}

	private static SVNRevision getEndRevision() {
		return getRevisionOrHead(config().getEndRevision());
	}

	public static SVNRevision getRevisionOrHead(long revision) {
		if (revision < 1) {
			return SVNRevision.HEAD;
		} else {
			return SVNRevision.create(revision);
		}
	}

	private static SVNLogEntryMatcher newLogEntryMatcher(TracConnection trac, PortingTickets portingTickets) throws MalformedURLException {
		return new DefaultLogEntryMatcher(trac, config(), portingTickets);
	}

	private static TracConnection createTracConnection(LoginCredential tracCredentials) throws MalformedURLException {
		return new TracConnection(config().getTracURL(), tracCredentials.getUser(),
			tracCredentials.getPasswd());
	}

	private static CommitHandler newCommitHandler(MessageRewriter messageRewriter) {
		return new CommitHandler(config(), _modules, messageRewriter);
	}

	private static String[] getPaths(Configuration config) {
		return getModulePaths(config.getSourceBranch(), _modules);
	}

	public static String[] getModulePaths(String branch, Collection<String> modules) {
		String[] pathes = new String[modules.size()];
		int n = 0;
		for (String module : modules) {
			pathes[n++] = branch + Utils.SVN_SERVER_PATH_SEPARATOR + module;
		}
		return pathes;
	}

	public static SVNClientManager newSVNClientManager() throws IOException {
		LoginCredential svnCredentials = PropertiesUtil.load("conf/loginCredentials.properties", "svn.",
				LoginCredential.class);
		DefaultSVNOptions options = new DefaultSVNOptions() {
			@Override
			public ISVNMerger createMerger(byte[] conflictStart, byte[] conflictSeparator, byte[] conflictEnd) {
				return new ContentSensitiveMerger(conflictStart, conflictSeparator, conflictEnd, getConflictResolver(),
					SVNDiffConflictChoiceStyle.CHOOSE_MODIFIED_LATEST);
			}
		};
		ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(svnCredentials.getUser(),
				svnCredentials.getPasswd());
		return SVNClientManager.newInstance(options, authManager);
	}

}