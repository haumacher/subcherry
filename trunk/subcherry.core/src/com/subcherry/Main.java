package com.subcherry;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import com.subcherry.commit.CommitHandler;
import com.subcherry.log.DirCollector;
import com.subcherry.merge.MergeHandler;
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

	private static Configuration _config;

	private static Set<String> _modules;

	public static void main(String[] args) throws IOException, SVNException {
		installConfiguration();

		LoginCredential tracCredentials = PropertiesUtil.load("conf/loginCredentials.properties", "trac.",
				LoginCredential.class);

		doMerge(tracCredentials);
	}

	public static void doMerge(LoginCredential tracCredentials) throws SVNException, IOException {
		SVNRevision startRevision = _config.getRevert() ? getEndRevision() : getStartRevision();
		SVNRevision endRevision = _config.getRevert() ? getStartRevision() : getEndRevision();
		SVNRevision pegRevision = endRevision;
		boolean stopOnCopy = false;
		boolean discoverChangedPaths = true;
		long limit = NO_LIMIT;
		SVNClientManager clientManager = newSVNClientManager();
		SVNLogClient logClient = clientManager.getLogClient();
		
		SVNURL sourceBranchUrl = SVNURL.parseURIDecoded(_config.getSvnURL() + Utils.SVN_SERVER_PATH_SEPARATOR + _config.getSourceBranch());
		SVNURL targetBranchUrl = SVNURL.parseURIDecoded(_config.getSvnURL() + Utils.SVN_SERVER_PATH_SEPARATOR + _config.getTargetBranch());
		if (_config.getDetectCommonModules()) {
			_modules = DirCollector.getCommonBranchModules(logClient, _config.getModules(), sourceBranchUrl, targetBranchUrl, pegRevision);
		} else {
			_modules = new HashSet<String>(Arrays.asList(_config.getModules()));
		}
		Log.info("Merging modules: " + _modules);
		
		SVNLogEntryMatcher logEntryMatcher = newLogEntryMatcher(tracCredentials);
		CommitHandler commitHandler = newCommitHndler();
		MergeHandler mergeHandler = new MergeHandler(_config, _modules);
		SVNURL url = SVNURL.parseURIDecoded(_config.getSvnURL());
		String[] paths = getPaths(_config);

		logClient.doLog(url, paths, pegRevision, startRevision, endRevision, stopOnCopy, discoverChangedPaths, limit,
				logEntryMatcher);
		
		for (SVNLogEntry entry : logEntryMatcher.getEntries()) {
			System.out.println("[" + entry.getRevision() + "]: " + MergeCommitHandler.encode(entry.getMessage()));
		}
		Log.info("Start merging " + logEntryMatcher.getEntries().size() + " revisions.");

		MergeCommitHandler mergeCommitHandler = new MergeCommitHandler(logEntryMatcher.getEntries(), mergeHandler, commitHandler, clientManager, _config);
		mergeCommitHandler.run();

		Restart.clear();
	}

	public static SVNRevision getStartRevision() {
		long storedRevision = Restart.getRevision();
		if (storedRevision != Restart.NO_REVISION_FOUND) {
			return SVNRevision.create(storedRevision);
		} else {
			return getRevisionOrHead(_config.getStartRevision());
		}
	}

	private static SVNRevision getEndRevision() {
		return getRevisionOrHead(_config.getEndRevision());
	}

	public static SVNRevision getRevisionOrHead(long revision) {
		if (revision < 1) {
			return SVNRevision.HEAD;
		} else {
			return SVNRevision.create(revision);
		}
	}

	private static SVNLogEntryMatcher newLogEntryMatcher(LoginCredential tracCredentials) throws MalformedURLException {
		SVNLogEntryMatcher logEntryMatcher = new DefaultLogEntryMatcher(tracCredentials, _config);
		return logEntryMatcher;
	}

	private static CommitHandler newCommitHndler() {
		return new CommitHandler(_config, _modules);
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
		DefaultSVNOptions options = new DefaultSVNOptions();
		ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(svnCredentials.getUser(),
				svnCredentials.getPasswd());
		return SVNClientManager.newInstance(options, authManager);
	}

	private static void installConfiguration() throws IOException {
		if (_config == null) {
			_config = PropertiesUtil.load("conf/configuration.properties", Configuration.class);
		}
	}

}
