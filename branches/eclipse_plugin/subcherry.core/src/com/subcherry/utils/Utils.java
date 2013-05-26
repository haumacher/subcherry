package com.subcherry.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class Utils {

	public static class TicketMessage {
		public Matcher matcher;
		public String ticketNumber;
		public String apiChange;
		public String originalMessage;
		
		public TicketMessage(String commitMessage) {
			matcher = TICKET_PATTERN.matcher(commitMessage);
			if (!matcher.matches()) {
				throw new IllegalArgumentException("Message " + commitMessage  + " has illegal format."); 
			}
			ticketNumber = getTicketNumber(matcher);
			apiChange = getApiChange(matcher);
			originalMessage = getOriginalMessage(matcher);
		}
	}

	private static int group = 1;
	
	private static int ORIG_MESSAGE_GROUP;
	private static int HOTFIX_BRANCH_GROUP;
	private static int IS_HOTFIX_GROUP;
	private static int API_CHANGE_GROUP;
	private static int TICKET_NUMBER_GROUP;
	private static int PREVIEW_BRANCH_GROUP;
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
		return "^" + ticketPrefix() + "(?:" + fromPrefix() + ")?" + "(?:" + apiChangePrefix() + ")?" + "(?:" + mergeRevisionPrefix() + ")?" + group(ORIG_MESSAGE_GROUP = group, originalMessage()) + "$";
	}
	private static String ticketPrefix() {
		return "Ticket #" + group(TICKET_NUMBER_GROUP = group, "\\d+") + ":";
	}
	private static String fromPrefix() {
		return messagePart(or(hotfix(), preview(), ported()));
	}
	private static String hotfix() {
		return group(IS_HOTFIX_GROUP = group, "Hotfix for " + group(HOTFIX_BRANCH_GROUP = group, "[^:]*"));
	}
	private static String preview() {
		return "Preview on " + group(PREVIEW_BRANCH_GROUP = group, "[^:]*");
	}
	private static String ported() {
		return "Ported to " + group(PORTED_TO_GROUP = group, "[^ ]*") + " from " + group(PORTED_FROM_GROUP = group, "[^:]*");
	}
	private static String apiChangePrefix() {
		return group(API_CHANGE_GROUP = group, messagePart("API change"));
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
	public static boolean isHotfix(Matcher matcher) {
		return matcher.group(IS_HOTFIX_GROUP) != null;
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

