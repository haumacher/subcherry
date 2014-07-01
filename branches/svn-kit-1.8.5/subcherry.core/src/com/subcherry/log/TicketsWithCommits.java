package com.subcherry.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.UndeclaredThrowableException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import com.subcherry.trac.TracConnection;
import com.subcherry.trac.TracTicket;
import com.subcherry.utils.TeeWriter;

public class TicketsWithCommits {
	private static final String REVERTED_KEYWORD = "REVERTED";
	
	private static final String NO_PORT_KEYWORD_PREFIX = "NOPORT_";

	private static final String QUERY_NAME = "query.name";
	
	private static final String QUERY_BRANCH_CATEGORY_PATTERN_PROPERTY = "query.branchCategoryPattern";

	private static final String QUERY_MODULES_PROPERTY = "query.modules";

	private static final String TRAC_URL_PROPERTY = "trac.url";

	private static final String TRAC_PASSWORD_PROPERTY = "trac.user.password";

	private static final String TRAC_USER_PROPERTY = "trac.user.name";

	private static final String QUERY_FILE = "query.file";
	
	private static final String QUERY_TO_PROPERTY = "query.to";

	private static final String QUERY_PAGENAME_PROPERTY = "query.pagename";
	
	private static final String QUERY_UPDATE_COMMENT_PROPERTY = "query.updateComment";
	
	private static final String QUERY_PAGE_HEADER_PROPERTY = "query.pageHeader";
	
	private static final String QUERY_PAGE_FOOTER_PROPERTY = "query.pageFooter";
	
	private static final String QUERY_MILESTONE = "query.milestone";
	
	private static final String QUERY_COMPONENT_PROPERTY = "query.component";
	
	private static final String QUERY_EXCLUDE_CLOSED_PROPERTY = "query.excludeClosed";
	
	private static final String QUERY_EXCLUDE_PORTED_PROPERTY = "query.excludePorted";
	
	private static final String QUERY_EXCLUDE_KEYWORDS_PROPERTY = "query.excludeKeywords";
	
	private static final String QUERY_LOG_INVALID_PROPERTY = "query.logInvalid";
	
	private static final String QUERY_SKIP_INVALID_PROPERTY = "query.skipInvalid";
	
	private static final String QUERY_FROM_PROPERTY = "query.from";

	private static final String SVN_COMMIT_MESSAGE_PATTERN_PROPERTY = "svn.commitMessagePattern";
	
	private static final String SVN_COMMIT_MESSAGE_IGNORE_PATTERN_PROPERTY = "svn.commitMessageIgnorePattern";
	
	public static void main(String[] args) throws SVNException, IOException, ParseException {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");

		File settingsFile = new File("settings.properties");
		Properties globalProperties = SettingsUtil.loadConfig(settingsFile);
		
		SVNSettings svn = SVNSettingsImpl.loadSettings(in, globalProperties);
		
		String commitMessagePatternSource = inputOnce(in, "Commit message pattern (with single group determining the ticket number)", globalProperties.getProperty(SVN_COMMIT_MESSAGE_PATTERN_PROPERTY));
		String commitMessageIgnorePatternSource = inputOnce(in, "Ignorable commit message pattern", globalProperties.getProperty(SVN_COMMIT_MESSAGE_IGNORE_PATTERN_PROPERTY));
		
		String tracUrl = inputOnce(in, "Trac URL", globalProperties.getProperty(TRAC_URL_PROPERTY));
		String tracUser = inputOnce(in, "Trac user name", globalProperties.getProperty(TRAC_USER_PROPERTY));
		String tracPassword = inputOnce(in, "Trac password", globalProperties.getProperty(TRAC_PASSWORD_PROPERTY));
		
		String branchCategoryPattern =
			inputOnce(in, "Branch category pattern", globalProperties.getProperty(QUERY_BRANCH_CATEGORY_PATTERN_PROPERTY));
		String queryComponentPatternSource = inputOnce(in, "Allowed ticket component pattern", globalProperties.getProperty(QUERY_COMPONENT_PROPERTY));
		
		String querySettings = input(in, "Settings", globalProperties.getProperty(QUERY_FILE));
		
		{
			Properties updatedProperties = new Properties();
			SVNSettingsImpl.saveSettings(svn, updatedProperties);
			updatedProperties.setProperty(SVN_COMMIT_MESSAGE_PATTERN_PROPERTY, commitMessagePatternSource);
			updatedProperties.setProperty(SVN_COMMIT_MESSAGE_IGNORE_PATTERN_PROPERTY, commitMessageIgnorePatternSource);

			updatedProperties.setProperty(TRAC_USER_PROPERTY, tracUser);
			updatedProperties.setProperty(TRAC_PASSWORD_PROPERTY, tracPassword);
			updatedProperties.setProperty(TRAC_URL_PROPERTY, tracUrl);

			updatedProperties.setProperty(QUERY_BRANCH_CATEGORY_PATTERN_PROPERTY, branchCategoryPattern);
			updatedProperties.setProperty(QUERY_COMPONENT_PROPERTY, queryComponentPatternSource);
			updatedProperties.setProperty(QUERY_FILE, querySettings);
			
			SettingsUtil.saveConfig(settingsFile, updatedProperties);
		}
		
		File queryFile = new File(querySettings);
		Properties queryProperties = SettingsUtil.loadConfig(queryFile);
		
		String modulesString = input(in, "Modules", queryProperties.getProperty(QUERY_MODULES_PROPERTY));

		String from = input(in, "Von Datum (YYYY-MM-DD)", queryProperties.getProperty(QUERY_FROM_PROPERTY));
		String to = input(in, "Bis Datum (YYYY-MM-DD)", queryProperties.getProperty(QUERY_TO_PROPERTY));

		boolean excludeClosed = Boolean.valueOf(input(in, "Exclude closed", queryProperties.getProperty(QUERY_EXCLUDE_CLOSED_PROPERTY)));
		boolean excludePorted = Boolean.valueOf(input(in, "Exclude ported", queryProperties.getProperty(QUERY_EXCLUDE_PORTED_PROPERTY)));
		String excludeKeywords = input(in, "Exclude keywords", queryProperties.getProperty(QUERY_EXCLUDE_KEYWORDS_PROPERTY));
		boolean logInvalidCommits = Boolean.valueOf(input(in, "Log invalid commits", queryProperties.getProperty(QUERY_LOG_INVALID_PROPERTY)));
		boolean skipInvalidCommits = Boolean.valueOf(input(in, "Skip invalid commits", queryProperties.getProperty(QUERY_SKIP_INVALID_PROPERTY)));
		String queryMilestone = input(in, "Milestone", queryProperties.getProperty(QUERY_MILESTONE));
		String queryName = input(in, "Query name", queryProperties.getProperty(QUERY_NAME));
		String pagename = input(in, "Page to store results", queryProperties.getProperty(QUERY_PAGENAME_PROPERTY));
		String updateComment = input(in, "Page change comment", queryProperties.getProperty(QUERY_UPDATE_COMMENT_PROPERTY));
		String header = input(in, "Page header", queryProperties.getProperty(QUERY_PAGE_HEADER_PROPERTY));
		String footer = input(in, "Page footer", queryProperties.getProperty(QUERY_PAGE_FOOTER_PROPERTY));
		
		// Parent branches for branches (branches to which a patch must be ported, if done on a child branch).
		Map<String, String> branchTree = new HashMap<String, String>();
		branchTree.put("/branches/TL/TL_5_6_1_x", "/branches/TL/TL_5_6_2_x");
		branchTree.put("/branches/TL/TL_5_6_2_x", "/trunk");
		
		{
			Properties updatedProperties = new Properties();
			updatedProperties.setProperty(QUERY_NAME, queryName);
			updatedProperties.setProperty(QUERY_MODULES_PROPERTY, modulesString);
			updatedProperties.setProperty(QUERY_FROM_PROPERTY, from);
			updatedProperties.setProperty(QUERY_TO_PROPERTY, to);
			updatedProperties.setProperty(QUERY_EXCLUDE_CLOSED_PROPERTY, Boolean.toString(excludeClosed));
			updatedProperties.setProperty(QUERY_EXCLUDE_PORTED_PROPERTY, Boolean.toString(excludePorted));
			updatedProperties.setProperty(QUERY_EXCLUDE_KEYWORDS_PROPERTY, excludeKeywords);
			updatedProperties.setProperty(QUERY_LOG_INVALID_PROPERTY, Boolean.toString(logInvalidCommits));
			updatedProperties.setProperty(QUERY_SKIP_INVALID_PROPERTY, Boolean.toString(skipInvalidCommits));
			updatedProperties.setProperty(QUERY_MILESTONE, queryMilestone);
			updatedProperties.setProperty(QUERY_PAGENAME_PROPERTY, pagename);
			updatedProperties.setProperty(QUERY_UPDATE_COMMENT_PROPERTY, updateComment);
			updatedProperties.setProperty(QUERY_PAGE_HEADER_PROPERTY, header);
			updatedProperties.setProperty(QUERY_PAGE_FOOTER_PROPERTY, footer);
			
			SettingsUtil.saveConfig(queryFile, updatedProperties);
		}

		Pattern queryMilestonePattern = Pattern.compile(queryMilestone); 
		Pattern queryComponentPattern = Pattern.compile(queryComponentPatternSource); 

		List<String> modules = new ArrayList<String>();
		StringTokenizer moduleTokens = new StringTokenizer(modulesString, " \t\n\r,", false);
		while (moduleTokens.hasMoreTokens()) {
			modules.add(moduleTokens.nextToken());
		}

		List<String> excludeKeywordsList = new ArrayList<String>();
		StringTokenizer excludeKeywordsTokens = new StringTokenizer(excludeKeywords, " \t\n\r,", false);
		while (excludeKeywordsTokens.hasMoreTokens()) {
			excludeKeywordsList.add(excludeKeywordsTokens.nextToken());
		}
		
		TracConnection trac = new TracConnection(tracUrl, tracUser, tracPassword);

		DAVRepositoryFactory.setup();
		ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(svn.getUser(), svn.getPassword());

		SVNURL url = SVNURL.parseURIDecoded(svn.getUrl());
		SVNRepository repository = SVNRepositoryFactory.create(url, null);

		// set an auth manager which will provide user credentials
		repository.setAuthenticationManager(authManager);

		long startRevision;
		if (from != null && from.length() > 0) {
			Date startDate = dayFormat.parse(from);
			startRevision = repository.getDatedRevision(startDate);
		} else {
			startRevision = 1;
		}

		long endRevision;
		if (to != null && to.length() > 0) {
			Date endDate = dayFormat.parse(to);
			endRevision = repository.getDatedRevision(endDate);
		} else {
			endRevision = Long.MAX_VALUE;
		}


		final Collection<SVNLogEntry> svnLog = repository.log(new String[] { "/", }, null, startRevision, endRevision, true, true);
		final Pattern pathPattern = createPathPattern(branchCategoryPattern, modules);
		final Pattern fileIgnorePattern = createFileIgnorePattern();
		final Pattern commitMessagePattern = Pattern.compile(commitMessagePatternSource, Pattern.DOTALL);
		final Pattern commitMessageIgnorePattern = Pattern.compile(commitMessageIgnorePatternSource, Pattern.DOTALL);

		
		TicketXRef xref = new TicketXRef(commitMessagePattern, commitMessageIgnorePattern, pathPattern, fileIgnorePattern, svnLog);

		StringWriter buffer = new StringWriter();
		PrintWriter out = new PrintWriter(new TeeWriter(buffer, new OutputStreamWriter(System.out)));

		if (header.length() > 0) {
			out.println(header);
		}
		out.println("= " + queryName + " =");
		out.println("Since " + from + ", last update " + dayFormat.format(new Date()) + ".");
		
		if (logInvalidCommits) {
			for (Entry<String, List<SVNLogEntry>> errors : xref.getInvalidCommitsByReason().entrySet()) {
				error(" * " + errors.getKey() + ": " + getCommitNumbers(errors.getValue()));
			}
		}
		
		Map<String, List<Integer>> pendingTicketsByUser = new HashMap<String, List<Integer>>();
		
		Map<Integer, TracTicket> ticketsById = new HashMap<Integer, TracTicket>();
		List<Integer> ticketIds = new ArrayList<Integer>(xref.getAllTicketIds());
		Collections.sort(ticketIds);
		allTickets:
		for (Integer ticketId : ticketIds) {
			TracTicket tracTicket;
			try {
				tracTicket = TracTicket.getTicket(trac, ticketId);
			} catch (UndeclaredThrowableException ex) {
				error(" * Ticket #" + ticketId + ": Cannot access.");
				continue;
			}
			ticketsById.put(ticketId, tracTicket);
			
			Set<String> commitBranches = xref.getCommitBranches(ticketId);
			if (tracTicket == null) {
				if (logInvalidCommits) {
					error(" * Ticket #" + ticketId + ": Invalid commits on " + commitBranches + " (Trac Ticket not found.): " + getCommitNumbers(xref, ticketId));
				}
				continue;
			}

			String keywordsSource = (String) tracTicket.getAttributeValue(TracTicket.TICKET_ATT_KEYWORD);
			Set<String> keywords = new HashSet<String>(Arrays.asList(keywordsSource.split("[, ]+")));
			
			for (String keyword : excludeKeywordsList) {
				if (keywords.contains(keyword)) {
					continue allTickets;
				}
			}
			
			String component = (String) tracTicket.getAttributeValue(TracTicket.TICKET_ATT_COMPONENT);
			if (! queryComponentPattern.matcher(component).matches()) {
				if (logInvalidCommits) {
					error(" * Ticket #" + ticketId + ": Invalid commits on " + commitBranches + " (Component not allowed.): " + getCommitNumbers(xref, ticketId));
				}
				if (skipInvalidCommits) {
					continue;
				}
			}

			String milestone = (String) tracTicket.getAttributeValue(TracTicket.TICKET_ATT_MILESTONE);
			if (! queryMilestonePattern.matcher(milestone).matches()) {
				continue;
			}

			if (excludeClosed) {
				String state = (String) tracTicket.getAttributeValue(TracTicket.TICKET_ATT_STATUS);
				if (state.equals("testing")) {
					continue;
				}
				
				if (state.equals("closed")) {
					String resolution = (String) tracTicket.getAttributeValue(TracTicket.TICKET_ATT_RESOLUTION);
					if (resolution.equals("fixed")) {
						continue;
					}
				}
			}
			
			HashSet<String> missingPorts = new HashSet<String>();
			if (excludePorted) {
				for (String branch : commitBranches) {
					for (String parentBranch = branchTree.get(branch); parentBranch != null; parentBranch = branchTree.get(parentBranch)) {
						if (commitBranches.contains(parentBranch)) {
							continue;
						}
						
						if (keywords.contains(NO_PORT_KEYWORD_PREFIX + getBranchMnemonic(parentBranch))) {
							continue;
						}
						
						missingPorts.add(parentBranch);
					}
				}
				if (missingPorts.isEmpty()) {
					continue;
				}
			}

			String owner = (String) tracTicket.getAttributeValue(TracTicket.TICKET_ATT_OWNER);
			
			TicketXRef.addList(pendingTicketsByUser, owner, ticketId);
		}

		out.println();
		for (String owner : sort(pendingTicketsByUser.keySet())) {
			out.println("== " + owner + " ==");

			for (Integer ticketId : sort(pendingTicketsByUser.get(owner))) {
				ArrayList<String> branches = new ArrayList<String>(xref.getCommitBranches(ticketId));
				Collections.sort(branches);
				StringBuilder branchesBuffer = new StringBuilder();
				for (int n = 0, cnt = branches.size(); n < cnt; n++) {
					if (n > 0) {
						branchesBuffer.append(", ");
					}
					branchesBuffer.append(branches.get(n));
				}

				out.println(" * Ticket #" + ticketId + ": " + ticketsById.get(ticketId).getAttributeValue(TracTicket.TICKET_ATT_SUMMARY));
				out.println("    * " + xref.getCommits(ticketId).size() + " commits on " + branchesBuffer + ":");
				
				int n = 0;
				for (SVNLogEntry log : xref.getCommits(ticketId)) {
					if (n++ == 10) {
						out.println("    * ...");
						break;
					}
					String message = log.getMessage().trim().replaceAll("\n", "\n      ");
					out.println("    * [" + log.getRevision() + "]: " + message);
				}
			}
		}
		if (header.length() > 0) {
			out.println();
			out.println(footer);
		}
		out.flush();

		if (pagename.length() > 0) {
			Hashtable<String, String> pageProperties = new Hashtable<String, String>();
			if (updateComment.length() > 0) {
				pageProperties.put("comment", updateComment);
			}
			trac.getWiki().putPage(pagename, buffer.toString(), pageProperties);
		}
	}

	private static Pattern createFileIgnorePattern() {
		return Pattern.compile(""
			+ "(?:" + "test/.*" + ")" + "|"
			+ "(?:" + "src/test/.*" + ")" + "|"
			+ "(?:" + "src/com/top_logic/layout/form/demo/.*" + ")" + "|"
			+ "(?:" + "webapps/top-logic/WEB-INF/layouts/layoutdemo/.*" + ")" + "|"
			+ "(?:" + "webapps/[^/]*/jsp/test/.*" + ")" + "|"
			+ "(?:" + "webapps/[^/]*/html/test/.*" + ")" + "|"
			+ "(?:" + "webapps/[^/]*/WEB-INF/conf/resources/.*" + ")"
			);
	}

	private static String getBranchMnemonic(String branch) {
		int separatorIndex = branch.lastIndexOf('/');
		return branch.substring(separatorIndex + 1);
	}

	public static StringBuilder getCommitNumbers(TicketXRef xref, Integer ticketId) {
		return getCommitNumbers(xref.getCommits(ticketId));
	}

	public static StringBuilder getCommitNumbers(List<SVNLogEntry> commits) {
		StringBuilder commitNumbers = new StringBuilder();
		for (SVNLogEntry entry : commits) {
			if (commitNumbers.length() > 0) {
				commitNumbers.append(", ");
			}
			commitNumbers.append('[');
			commitNumbers.append(entry.getRevision());
			commitNumbers.append(']');
		}
		return commitNumbers;
	}

	static boolean hasErrors = false;
	
	private static void error(String msg) {
		if (! hasErrors) {
			System.out.println();
			System.out.println("=== Errors ===");
			hasErrors = true;
		}
		System.out.println(msg);
	}

	private static Pattern createPathPattern(String branchCategoryPattern, List<String> modules) {
		StringBuilder modulePattern = new StringBuilder();
		for (int n = 0, cnt = modules.size(); n < cnt; n++) {
			if (n > 0) {
				modulePattern.append("|");
			}
			modulePattern.append("(?:");
			modulePattern.append("\\Q");
			modulePattern.append(modules.get(n));
			modulePattern.append("\\E");
			modulePattern.append(")");
		}

		String branchPattern = ""
			+ 	"(?:" + "/trunk" + ")"
			+ 	"|"
			+ 	"(?:" + "/branches/" + branchCategoryPattern + "/[^/]+" + ")";
		
		String filePattern = ".*";
		
		String patternSource = "" 
			+ "^"
			+ "("
			+ 	branchPattern
			+ ")"
			+ "/"
			+ "(?:"
			+ 	modulePattern
			+ ")"
			+ "/"
			+ "("
			+ 	filePattern
			+ ")"
			+ "$";
		
		return Pattern.compile(patternSource, Pattern.DOTALL);
	}

	static String inputOnce(BufferedReader in, String inputMessage, String defaultValue) throws IOException {
		if (defaultValue != null) {
			return defaultValue;
		}
		return input(in, inputMessage, defaultValue);
	}

	private static String input(BufferedReader in, String inputMessage, String defaultValue) throws IOException {
		System.out.print(inputMessage + defaultValue(defaultValue) + ": ");
		String result = read(in, defaultValue);
		if (result == null) {
			return "";
		} else {
			return result;
		}
	}

	private static String read(BufferedReader in, String defaultValue) throws IOException {
		String newUser = in.readLine();
		if (newUser.trim().length() > 0) {
			defaultValue = newUser;
		}
		return defaultValue;
	}

	private static String defaultValue(String value) {
		if (value == null) {
			return "";
		} else {
			return " (" + value + ")";
		}
	}

	private static <T extends Comparable<? super T>> List<T> sort(Collection<T> c) {
		ArrayList<T> result = new ArrayList<T>(c);
		Collections.sort(result);
		return result;
	}

}
