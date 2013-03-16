package com.subcherry.log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import com.subcherry.DefaultLogEntryMatcher;
import com.subcherry.LoginCredential;
import com.subcherry.Main;
import com.subcherry.MergeCommitHandler;
import com.subcherry.SVNConfig;
import com.subcherry.configuration.ConfigurationFactory;
import com.subcherry.trac.TracConnection;
import com.subcherry.utils.Utils;

/**
 * Tool to find ticket that have commits on a certain branch.
 * 
 * @version   $Revision$  $Author$  $Date$
 */
public class TicketsOnBranch {
	
	static final Logger LOG = Logger.getLogger(TicketsOnBranch.class.getName());
	
	public interface LogOptions {
		String[] getExcludedTickets();
		String getExcludedQuery();
		String[] getAdditionalTickets();
		String[] getModules();
		long getStartRevision();
		long getEndRevision();
		boolean getStopOnCopy();
		String getBranch();
		String getIgnorePattern();
	}

	private static final String[] NO_PROPERTIES = {};

	public static void main(String[] args) throws IOException, SVNException {
		LOG.setLevel(Level.FINE);
		ConsoleHandler logHandler = new ConsoleHandler();
		logHandler.setLevel(Level.FINE);
		LOG.addHandler(logHandler);
		
		SVNClientManager svnClient = Main.newSVNClientManager();
		SVNLogClient logClient = svnClient.getLogClient();
		
		SVNConfig config = ConfigurationFactory.newConfiguration(SVNConfig.class, "conf/svnConfig.properties");
		LogOptions options = ConfigurationFactory.newConfiguration(LogOptions.class, "conf/logOptions.properties");
		
		final Pattern ticketPattern = Pattern.compile(config.getTicketPattern());
		final Pattern ignorePatternOpt = Utils.compileOptional(options.getIgnorePattern());
		
		class Handler implements ISVNLogEntryHandler {
			private Map<String, List<Long>> commitsByTicket = new HashMap<String, List<Long>>();

			@Override
			public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
				String message = logEntry.getMessage();
				if (message == null) {
					LOG.fine("Ignored commit without message: [" + logEntry.getRevision() + "].");
					return;
				}
				
				if (ignorePatternOpt != null && ignorePatternOpt.matcher(message).lookingAt()) {
					LOG.fine("Ignored commit: [" + logEntry.getRevision() + "]: " + MergeCommitHandler.encode(message));
					return;
				}
				
				Matcher ticketMatcher = ticketPattern.matcher(message);
				if (!ticketMatcher.lookingAt()) {
					LOG.fine("Invalid commit: [" + logEntry.getRevision() + "]: " + MergeCommitHandler.encode(message));
					return;
				}
				
				String ticket = ticketMatcher.group(1);
				List<Long> commits = commitsByTicket.get(ticket);
				if (commits == null) {
					commits = new ArrayList<Long>();
					commitsByTicket.put(ticket, commits);
				}
				commits.add(logEntry.getRevision());
				
				LOG.fine("Ticket #" + ticket + " in [" + logEntry.getRevision() + "]: " + MergeCommitHandler.encode(message));
			}
			
			public Set<String> getTickets() {
				return commitsByTicket.keySet();
			}
			
			public Map<String, List<Long>> getCommitsByTicket() {
				return commitsByTicket;
			}
		}
		
		SVNURL rootUrl = SVNURL.parseURIDecoded(config.getSvnURL());
		SVNRevision endRevision = Main.getRevisionOrHead(options.getEndRevision());
		SVNURL branchUrl = SVNURL.parseURIDecoded(config.getSvnURL() + options.getBranch());
		
		Handler handler = new Handler();
		String[] configuredModules = options.getModules();
		Set<String> modules = DirCollector.getBranchModules(logClient, configuredModules, branchUrl, endRevision);
		
		String[] modulePaths = Main.getModulePaths(options.getBranch(), modules);
		SVNRevision startRevision = Main.getRevisionOrHead(options.getStartRevision());
		boolean stopOnCopy = options.getStopOnCopy();
		logClient.doLog(rootUrl, modulePaths, endRevision, startRevision, endRevision, stopOnCopy, false, true, 0, NO_PROPERTIES, handler);
		
		Set<String> tickets = new HashSet<String>(handler.getTickets());
		tickets.removeAll(Arrays.asList(options.getExcludedTickets()));
		if (isNotEmpty(options.getExcludedQuery())) {
			LoginCredential tracCredentials = ConfigurationFactory.newConfiguration(LoginCredential.class,
					"conf/loginCredentials.properties", "trac");
			TracConnection trac = new TracConnection(config.getTracURL(), tracCredentials.getUser(), tracCredentials.getPasswd());
			Vector<Integer> ids = trac.getTicket().query(options.getExcludedQuery());
			tickets.removeAll(DefaultLogEntryMatcher.toStringIds(ids));
		}
		
		tickets.addAll(Arrays.asList(options.getAdditionalTickets()));
		
		ArrayList<String> sortedTickets = new ArrayList<String>(tickets);
		Comparator<? super String> ticketComparator = new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return Integer.parseInt(o1) - Integer.parseInt(o2);
			}
		};
		Collections.sort(sortedTickets, ticketComparator);
		
		for (String ticket : sortedTickets) {
			System.out.print(" * Ticket #" + ticket + ": ");
			boolean first = true;
			for (Long commit : Utils.nonNull(handler.getCommitsByTicket().get(ticket))) {
				if (first) {
					first = false;
				} else {
					System.out.print(", ");
				}
				System.out.print("[" + commit + "]");
			}
			System.out.println();
		}
		
		System.out.print("http://tl/trac/query?");
		boolean first = true;
		for (String ticket : sortedTickets) {
			if (first) {
				first = false;
			} else {
				System.out.print("&or&");
			}
			System.out.print("id=");
			System.out.print(ticket);
		}
		System.out.println();
	}

	private static boolean isNotEmpty(String s) {
		return !isEmpty(s);
	}

	private static boolean isEmpty(String s) {
		return s == null || s.trim().isEmpty();
	}
	
}

