/*
 * SubCherry - Cherry Picking with Trac and Subversion
 * Copyright (C) 2014 Bernhard Haumacher and others
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.subcherry;

import static com.subcherry.Globals.*;
import static com.subcherry.utils.CollectionUtil.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNDiffConflictChoiceStyle;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.wc.DefaultSVNRepositoryPool;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

import com.subcherry.commit.Commit;
import com.subcherry.commit.CommitHandler;
import com.subcherry.commit.MessageRewriter;
import com.subcherry.commit.RevisionRewriter;
import com.subcherry.history.Change;
import com.subcherry.history.DependencyBuilder;
import com.subcherry.history.DependencyBuilder.Dependency;
import com.subcherry.history.HistroyBuilder;
import com.subcherry.history.Node;
import com.subcherry.log.DirCollector;
import com.subcherry.merge.ContentSensitiveMerger;
import com.subcherry.merge.MergeHandler;
import com.subcherry.trac.TracConnection;
import com.subcherry.trac.TracTicket;
import com.subcherry.utils.Log;
import com.subcherry.utils.PathParser;
import com.subcherry.utils.Utils;
import com.subcherry.utils.Utils.IllegalMessageFormat;

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

	private static final Logger LOG = Globals.logger(Main.class);

	private static final String NO_TICKET_ID = "";

	private static final Comparator<Node> PATH_ORDER = new Comparator<Node>() {
		@Override
		public int compare(Node n1, Node n2) {
			return n1.getPath().compareTo(n2.getPath());
		}
	};

	private static final String[] ROOT = { "/" };

	private static Set<String> _modules;

	public static void main(String[] args) throws IOException, SVNException {
		LoginCredential tracCredentials = PropertiesUtil.load("conf/loginCredentials.properties", "trac.",
				LoginCredential.class);

		doMerge(tracCredentials);
	}

	public static void doMerge(LoginCredential tracCredentials) throws SVNException, IOException {
		SVNRevision startRevision = getStartRevision();
		SVNRevision endRevision = getEndRevision();
		SVNRevision pegRevision = getPegRevision();
		SVNClientManager clientManager = newSVNClientManager();
		SVNLogClient logClient = clientManager.getLogClient();
		
		String sourceBranch = config().getSourceBranch();
		SVNURL sourceBranchUrl = SVNURL.parseURIDecoded(config().getSvnURL() + Utils.SVN_SERVER_PATH_SEPARATOR + sourceBranch);
		String targetBranch = config().getTargetBranch();
		SVNURL targetBranchUrl = SVNURL.parseURIDecoded(config().getSvnURL() + Utils.SVN_SERVER_PATH_SEPARATOR + targetBranch);
		if (config().getDetectCommonModules() || config().getModules().length == 0) {
			_modules = DirCollector.getBranchModules(logClient, config().getModules(), sourceBranchUrl, pegRevision);
		} else {
			_modules = new HashSet<String>(Arrays.asList(config().getModules()));
		}
		_modules.retainAll(getWorkspaceModules());
		Log.info("Merging modules: " + _modules);
		
		TracConnection trac = createTracConnection(tracCredentials);
		PortingTickets portingTickets = new PortingTickets(config(), trac);
		PathParser paths = new PathParser(config());
		MergeHandler mergeHandler = new MergeHandler(clientManager, config(), paths, _modules);
		MergeCommitHandler mergeCommitHandler =
			new MergeCommitHandler(mergeHandler, clientManager, config());
		RevisionRewriter revisionRewriter = mergeCommitHandler.getRevisionRewriter();
		MessageRewriter messageRewriter =
			MessageRewriter.createMessageRewriter(config(), portingTickets, revisionRewriter);
		SVNLogEntryMatcher logEntryMatcher = newLogEntryMatcher(trac, portingTickets);
		CommitHandler commitHandler = newCommitHandler(paths, messageRewriter);
		SVNURL url = SVNURL.parseURIDecoded(config().getSvnURL());

		if (config().getSkipWaitForTimestamp()) {
			SVNFileUtil.setSleepForTimestamp(false);
		}

		LOG.log(Level.INFO, "Reading source history.");
		LogReader logReader = new LogReader(logClient, url);

		logReader.setStartRevision(startRevision);
		logReader.setEndRevision(endRevision);
		logReader.setPegRevision(pegRevision);
		logReader.setStopOnCopy(false);
		logReader.setDiscoverChangedPaths(true);
		logReader.setLimit(NO_LIMIT);
		String[] sourcePaths = getLogPaths(sourceBranch);
		logReader.readLog(sourcePaths, logEntryMatcher);

		HashSet<Long> additionalRevisions = new HashSet<>(config().getAdditionalRevisions().keySet());
		boolean additionalRevisionsFromOtherBranches;
		if (!additionalRevisions.isEmpty()) {
			for (SVNLogEntry foundEntry : logEntryMatcher.getEntries()) {
				additionalRevisions.remove(foundEntry.getRevision());
			}

			additionalRevisionsFromOtherBranches = !additionalRevisions.isEmpty();
			if (additionalRevisionsFromOtherBranches) {
				LOG.log(Level.INFO, "Reading additional revisions from other branches.");

				// There are addition revisions not found on the source branch, load them
				// explicitly.
				for (Long additionalRev : additionalRevisions) {
					SVNRevision svnAdditionalRev = SVNRevision.create(additionalRev);
					logReader.setStartRevision(svnAdditionalRev);
					logReader.setEndRevision(svnAdditionalRev);
					logReader.readLog(ROOT, logEntryMatcher);
				}
			}
		} else {
			additionalRevisionsFromOtherBranches = false;
		}

		List<SVNLogEntry> mergedLogEntries = logEntryMatcher.getEntries();
		if (additionalRevisionsFromOtherBranches || config().getRevert()) {
			int reverse = config().getRevert() ? -1 : 1;
			final int smaller = reverse * -1;
			final int greater = reverse * 1;
			Collections.sort(mergedLogEntries, new Comparator<SVNLogEntry>() {
				@Override
				public int compare(SVNLogEntry e1, SVNLogEntry e2) {
					long r1 = e1.getRevision();
					long r2 = e2.getRevision();
					if (r1 < r2) {
						return smaller;
					}
					if (r1 > r2) {
						return greater;
					}
					return 0;
				}
			});
		}

		LOG.log(Level.INFO, "Reading target history.");
		
		if (!config().getSkipDependencies()) {
			HistroyBuilder historyBuilder = new HistroyBuilder(getStartRevision().getNumber());
			String[] targetPaths = getLogPaths(targetBranch);
			String[] allPaths = concat(sourcePaths, targetPaths);
			// For history reconstruction, the history must always be read in ascending revision
			// order.
			logReader.setStartRevision(getStartRevision());
			logReader.setEndRevision(getEndRevision());
			logReader.readLog(allPaths, historyBuilder);
			analyzeDependencies(historyBuilder, sourceBranch, targetBranch, trac, mergedLogEntries);
		}

		List<CommitSet> commitSets = getCommitSets(commitHandler, mergedLogEntries);
		if (config().getReorderCommits()) {
			reorderCommits(commitSets);
		}
		for (CommitSet commitSet : commitSets) {
			commitSet.print(System.out);
		}
		Log.info("Start merging " + mergedLogEntries.size() + " revisions.");

		mergeCommitHandler.run(commitSets);

		Restart.clear();
	}

	private static void analyzeDependencies(HistroyBuilder historyBuilder, String sourceBranch, String targetBranch,
			TracConnection trac, List<SVNLogEntry> mergedLogEntries) throws IOException {
		LOG.log(Level.INFO, "Analyzing dependencies.");
		DependencyBuilder dependencyBuilder = new DependencyBuilder(sourceBranch, targetBranch, _modules);
		dependencyBuilder.analyzeConflicts(historyBuilder.getHistory(), mergedLogEntries);

		Map<Change, Dependency> dependencies = dependencyBuilder.getDependencies();
		if (!dependencies.isEmpty()) {
			LOG.log(Level.INFO, "Conflicts detected.");

			/**
			 * Mapping of missing changes to nodes where conflicts are expected to merged changes
			 * that are potentially in conflict with the missing change.
			 */
			Map<Change, Map<Node, List<Change>>> missingChanges = new HashMap<>();
			for (Dependency dependency : dependencies.values()) {
				Change conflictingChange = dependency.getChange();

				for (Entry<Change, Set<Node>> requirement : dependency.getRequiredChanges().entrySet()) {
					Change missingChange = requirement.getKey();

					for (Node conflictNode : requirement.getValue()) {
						mkList(mkMap(missingChanges, missingChange), conflictNode).add(conflictingChange);
					}
				}
			}

			/**
			 * Ticket IDs of missing tickets mapped to changes of those tickets that are causing
			 * conflicts.
			 */
			Map<String, List<Change>> requiredTickets = new HashMap<>();
			for (Change change : missingChanges.keySet()) {
				String ticketId = Utils.getTicketId(change.getMessage());
				if (ticketId == null) {
					ticketId = NO_TICKET_ID;
				}
				mkList(requiredTickets, ticketId).add(change);
			}

			ReportPrinter printer = new ReportPrinter();
			printer.startReport();
			for (String ticketId : keysSorted(requiredTickets)) {
				TracTicket ticket;
				if (ticketId.equals(NO_TICKET_ID)) {
					ticket = null;
				} else {
					ticket = TracTicket.getTicket(trac, Integer.parseInt(ticketId));

					if (matches(config().getDependencyReport().getExcludeTicketMilestone(), ticket.getMilestone())) {
						continue;
					}
				}

				printer.setTicket(ticketId, ticket);

				List<Change> requiredChangesFromTicket = requiredTickets.get(ticketId);
				Collections.sort(requiredChangesFromTicket, ChangeOrder.INSTANCE);
				for (Change missingChange : requiredChangesFromTicket) {
					printer.setMissingChange(missingChange);

					Map<Node, List<Change>> fileConflicts = missingChanges.get(missingChange);
					for (Node conflictNode : keysSorted(fileConflicts, PATH_ORDER)) {
						if (matches(config().getDependencyReport().getExcludePath(), conflictNode.getPath())) {
							continue;
						}

						printer.setConflictNode(conflictNode);

						List<Change> conflicts = fileConflicts.get(conflictNode);
						Collections.sort(conflicts, ChangeOrder.INSTANCE);
						for (Change conflict : conflicts) {
							printer.printConflictingChange(conflict);
						}
					}
				}
				printer.endTicket();
			}
			printer.endReport();

			if (printer.hasConflictsReported()) {
				System.out.print("Continue (yes/no)? ");
				String input = Utils.SYSTEM_IN.readLine();
				if (!input.equals("yes")) {
					System.out.println("Stopping.");
					System.exit(1);
				}
			}
		}
	}

	private static String[] concat(String[] s1, String[] s2) {
		String[] result = new String[s1.length + s2.length];
		System.arraycopy(s1, 0, result, 0, s1.length);
		System.arraycopy(s2, 0, result, s1.length, s2.length);
		return result;
	}

	private static boolean matches(Pattern pattern, String text) {
		if (pattern == null) {
			return false;
		}

		return pattern.matcher(text).find();
	}

	private static String[] getSourcePaths() {
		return getLogPaths(config().getSourceBranch());
	}

	private static String[] getTargetPaths() {
		return getLogPaths(config().getTargetBranch());
	}

	private static String[] getLogPaths(String sourceBranch) {
		Set<String> modules = _modules;
		String[] paths = new String[modules.size() + 1];

		// Add paths for each concrete module: This is done because the module could have been
		// copied to the branch. In this case the changes in the module before copy time are not
		// logged for the whole branch, but for the concrete module.
		int i = 0;
		StringBuilder path = new StringBuilder();
		for (String module : modules) {
			path.append(sourceBranch);
			if (path.charAt(path.length() - 1) != '/')
				path.append('/');
			path.append(module);
			paths[i++] = path.toString();
			path.setLength(0);
		}

		// Add also whole branch to get changes like deletion or copying of modules which are not
		// logged for the module itself.
		paths[i] = sourceBranch;
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
				commitSetByLeadRevision.put(commitSet.getLeadCommit().getRevision(), commitSet);
			} else {
				setIt.remove();
			}
		}

		toOriginalOrder(commitSets);
	}

	/**
	 * Reorder {@link CommitSet}s to their order in which their lead commits have occurred
	 * originally.
	 */
	private static void toOriginalOrder(List<CommitSet> commitSets) {
		class Order {
			public final long _mergedRev;

			public final int _origIndex;

			public final CommitSet _value;

			public Order(long mergedRev, int origIndex, CommitSet value) {
				_mergedRev = mergedRev;
				_origIndex = origIndex;
				_value = value;
			}
		}

		// Order by their original revisions.
		final List<Order> indices = new ArrayList<>();
		int index = 0;
		for (CommitSet commitSet : commitSets) {
			String mergedRevision = commitSet.getLeadCommit().getTicketMessage().getMergedRevision();
			long mergedRev;
			if (mergedRevision != null) {
				mergedRev = Long.parseLong(mergedRevision);
			} else {
				// Use original revision instead. This allows replacing arbitrary changes during a
				// rebase with original changes. This is useful to redo merges of certain change
				// sets while keeping conflict resolutions from a previous rebase run.
				mergedRev = commitSet.getLeadCommit().getRevision();
			}
			indices.add(new Order(mergedRev, index++, commitSet));
		}

		Collections.sort(indices, new Comparator<Order>() {
			@Override
			public int compare(Order cs1, Order cs2) {
				long revision1 = cs1._mergedRev;
				long revision2 = cs2._mergedRev;
				if (revision1 < revision2) {
					return -1;
				}
				if (revision1 > revision2) {
					return 1;
				}

				int orig1 = cs1._origIndex;
				int orig2 = cs2._origIndex;
				if (orig1 < orig2) {
					return -1;
				}
				if (orig1 > orig2) {
					return 1;
				}
				return 0;
			}
		});

		commitSets.clear();
		for (Order order : indices) {
			commitSets.add(order._value);
		}
	}

	private static List<CommitSet> getCommitSets(CommitHandler commitHandler, List<SVNLogEntry> logEntries) {
		ArrayList<CommitSet> result = new ArrayList<CommitSet>(logEntries.size());
		for (SVNLogEntry logEntry : logEntries) {
			Commit commit;
			try {
				commit = commitHandler.parseCommit(logEntry);
			} catch (IllegalMessageFormat ex) {
				LOG.log(Level.WARNING, "Unable to parse commit for log entry:" + logEntry, ex);
				continue;
			}
			result.add(new CommitSet(logEntry, commit));
		}
		return result;
	}

	private static SVNRevision getPegRevision() {
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

	private static CommitHandler newCommitHandler(PathParser paths, MessageRewriter messageRewriter) {
		return new CommitHandler(config(), messageRewriter);
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
		SVNWCContext svnwcContext = new SVNWCContext(options, null);
		SvnOperationFactory svnOperationFactory = new SvnOperationFactory(svnwcContext);
		svnOperationFactory.setRepositoryPool(new DefaultSVNRepositoryPool(authManager, options));
		svnOperationFactory.setAutoDisposeRepositoryPool(true);
		return SVNClientManager.newInstance(svnOperationFactory);
	}

}
