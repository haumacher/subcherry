package com.subcherry;

import java.io.File;
import java.io.FileFilter;
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
import com.subcherry.commit.MessageRewriter;
import com.subcherry.log.DirCollector;
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
		SVNRevision pegRevision = getPegRevision(startRevision);
		boolean stopOnCopy = false;
		boolean discoverChangedPaths = true;
		long limit = NO_LIMIT;
		SVNClientManager clientManager = newSVNClientManager();
		SVNLogClient logClient = clientManager.getLogClient();
		
		SVNURL sourceBranchUrl = SVNURL.parseURIDecoded(_config.getSvnURL() + Utils.SVN_SERVER_PATH_SEPARATOR + _config.getSourceBranch());
		SVNURL targetBranchUrl = SVNURL.parseURIDecoded(_config.getSvnURL() + Utils.SVN_SERVER_PATH_SEPARATOR + _config.getTargetBranch());
		if (_config.getDetectCommonModules() || _config.getModules().length == 0) {
			_modules = DirCollector.getBranchModules(logClient, _config.getModules(), sourceBranchUrl, pegRevision);
		} else {
			_modules = new HashSet<String>(Arrays.asList(_config.getModules()));
		}
		_modules.retainAll(getWorkspaceModules());
		Log.info("Merging modules: " + _modules);
		
		TracConnection trac = createTracConnection(tracCredentials);
		PortingTickets portingTickets = new PortingTickets(_config, trac);
		MessageRewriter messageRewriter = new MessageRewriter(_config, portingTickets);
		SVNLogEntryMatcher logEntryMatcher = newLogEntryMatcher(trac, portingTickets);
		CommitHandler commitHandler = newCommitHandler(messageRewriter);
		MergeHandler mergeHandler = new MergeHandler(_config, _modules);
		SVNURL url = SVNURL.parseURIDecoded(_config.getSvnURL());
		String[] paths = {_config.getSourceBranch()};

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

	private static SVNRevision getPegRevision(SVNRevision startRevision) {
		return getRevisionOrHead(_config.getPegRevision());
	}

	private static Set<String> getWorkspaceModules() {
		File workspaceRoot = _config.getWorkspaceRoot();
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

	private static SVNLogEntryMatcher newLogEntryMatcher(TracConnection trac, PortingTickets portingTickets) throws MalformedURLException {
		return new DefaultLogEntryMatcher(trac, _config, portingTickets);
	}

	private static TracConnection createTracConnection(LoginCredential tracCredentials) throws MalformedURLException {
		return new TracConnection(_config.getTracURL(), tracCredentials.getUser(),
			tracCredentials.getPasswd());
	}

	private static CommitHandler newCommitHandler(MessageRewriter messageRewriter) {
		return new CommitHandler(_config, _modules, messageRewriter);
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
