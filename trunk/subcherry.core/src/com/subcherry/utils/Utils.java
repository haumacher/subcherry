package com.subcherry.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.subcherry.commit.MessageRewriter;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class Utils {

	public static class TicketMessage {
		private static final MessageRewriter NO_REWRITE = null;

		public Matcher matcher;
		public String ticketNumber;
		public String apiChange;
		public String originalMessage;

		private final MessageRewriter _messageRewriter;

		private final long _originalRevision;

		private final String _commitMessage;

		private final long _leadRevision;
		
		public TicketMessage(String commitMessage) {
			this(0, commitMessage, NO_REWRITE);
		}

		public TicketMessage(long originalRevision, String commitMessage, MessageRewriter messageRewriter) {
			_originalRevision = originalRevision;
			_commitMessage = commitMessage;
			_messageRewriter = messageRewriter;

			matcher = TICKET_PATTERN.matcher(commitMessage);
			if (!matcher.matches()) {
				throw new IllegalArgumentException("Message " + commitMessage  + " has illegal format."); 
			}
			ticketNumber = getTicketNumber(matcher);
			apiChange = getApiChange(matcher);
			originalMessage = getOriginalMessage(matcher);
			_leadRevision = extractLeadRevision(matcher);
		}

		public boolean isHotfix() {
			return matcher.group(Utils.IS_HOTFIX_GROUP) != null;
		}
		
		public boolean isPreview() {
			return matcher.group(Utils.PREVIEW_BRANCH_GROUP) != null;
		}
		
		public boolean isBranchChange() {
			return matcher.group(Utils.ON_BRANCH_GROUP) != null;
		}

		public boolean isPort() {
			return matcher.group(Utils.PORTED_FROM_GROUP) != null;
		}

		public String getLogEntryMessage() {
			return _commitMessage;
		}

		public String getMergeMessage() {
			return _messageRewriter.getMergeMessage(_originalRevision, this);
		}

		public long getLeadRevision() {
			return _leadRevision;
		}
	}

	private static int group = 1;
	
	private static int ORIG_MESSAGE_GROUP;
	private static int HOTFIX_BRANCH_GROUP;
	private static int IS_HOTFIX_GROUP;
	private static int API_CHANGE_GROUP;

	private static int FOLLOW_UP_GROUP;
	private static int TICKET_NUMBER_GROUP;
	private static int PREVIEW_BRANCH_GROUP;

	private static int ON_BRANCH_GROUP;
	private static int PORTED_FROM_GROUP;
	private static int PORTED_TO_GROUP;
	
	private static String group(int id, String regexp) {
		assert id != 0;
		group++;
		return "(" + regexp + ")";
	}
	private static String or(String ...regexps) {
		StringBuilder buffer = new StringBuilder();
		boolean first = true;
		for (String regexp : regexps) {
			if (first) {
				first = false;
			} else {
				buffer.append("|");
			}
			buffer.append(expr(regexp));
		}
		return expr(buffer.toString());
	}
	private static String expr(String regexp) {
		return "(?:" + regexp + ")";
	}
	public static Pattern TICKET_PATTERN = Pattern.compile(commitMessage(), Pattern.DOTALL);

	public static char SVN_SERVER_PATH_SEPARATOR = '/';
	
	public static  BufferedReader SYSTEM_IN = new BufferedReader(new InputStreamReader(System.in));

	private static String commitMessage() {
		return "^" +
			ticketPrefix() +
			"(?:" + fromPrefix() + ")?" +
			"(?:" + apiChangePrefix() + ")?" +
			"(?:" + followUpRevisionPrefix() + ")?" +
			"(?:" + mergeRevisionPrefix() + ")?" +
			group(ORIG_MESSAGE_GROUP = group, originalMessage()) + "$";
	}
	private static String ticketPrefix() {
		return "Ticket #" + group(TICKET_NUMBER_GROUP = group, "\\d+") + ":";
	}
	private static String fromPrefix() {
		return messagePart(or(hotfix(), preview(), ported(), onBranch()));
	}
	private static String hotfix() {
		return group(IS_HOTFIX_GROUP = group, "Hotfix for " + group(HOTFIX_BRANCH_GROUP = group, "[^:]*"));
	}
	private static String preview() {
		return "Preview on " + group(PREVIEW_BRANCH_GROUP = group, "[^:]*");
	}

	private static String onBranch() {
		return "On " + group(ON_BRANCH_GROUP = group, "[^:]*");
	}
	private static String ported() {
		return "Ported to " + group(PORTED_TO_GROUP = group, "[^ ]*") + " from " + group(PORTED_FROM_GROUP = group, "[^:]*");
	}
	private static String apiChangePrefix() {
		return group(API_CHANGE_GROUP = group, messagePart("API change"));
	}

	private static String followUpRevisionPrefix() {
		return messagePart("Follow-up (?:\\d+ )?for \\[" + group(FOLLOW_UP_GROUP = group, "\\d+") + "\\]");
	}

	private static String mergeRevisionPrefix() {
		return messagePart("\\[\\d+\\]");
	}
	private static String messagePart(String part) {
		return " " + part +  ":";
	}
	private static String originalMessage() {
		return ".*";
	}
	/** 
	 * @param matcher A {@link Matcher} matching {@link #TICKET_PATTERN}
	 */
	public static String getTicketNumber(Matcher matcher) {
		return matcher.group(TICKET_NUMBER_GROUP);
	}
	
	/** 
	 * @param matcher A {@link Matcher} matching {@link #TICKET_PATTERN}
	 */
	public static String getApiChange(Matcher matcher) {
		return matcher.group(API_CHANGE_GROUP);
	}
	
	/** 
	 * @param matcher A {@link Matcher} matching {@link #TICKET_PATTERN}
	 */
	public static String getHotfixBranch(Matcher matcher) {
		return matcher.group(HOTFIX_BRANCH_GROUP);
	}
	
	/** 
	 * @param matcher A {@link Matcher} matching {@link #TICKET_PATTERN}
	 */
	public static String getOriginalMessage(Matcher matcher) {
		return matcher.group(ORIG_MESSAGE_GROUP);
	}
	
	/**
	 * The revision to which the current one is a follow-up.
	 */
	public static long extractLeadRevision(Matcher matcher) {
		String leadRevision = matcher.group(FOLLOW_UP_GROUP);
		if (leadRevision == null) {
			return 0;
		}
		return Long.parseLong(leadRevision);
	}

	public static <T> Collection<T> nonNull(Collection<T> list) {
		if (list == null) {
			return Collections.emptyList();
		}
		return list;
	}

	public static <T> List<T> nonNull(List<T> list) {
		if (list == null) {
			return Collections.emptyList();
		}
		return list;
	}

	public static <T> Set<T> nonNull(Set<T> list) {
		if (list == null) {
			return Collections.emptySet();
		}
		return list;
	}

	public static Pattern compileOptional(String pattern) {
		if (pattern == null || pattern.isEmpty()) {
			return null;
		}
		return Pattern.compile(pattern);
	}

	public static String[] toStringArray(Collection<String> list) {
		return list.toArray(new String[list.size()]);
	}

	public static boolean isNullOrEmpty(String s) {
		return s == null || s.isEmpty();
	}

}

