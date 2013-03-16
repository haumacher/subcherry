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

	public static Pattern TICKET_PATTERN = Pattern.compile("^Ticket #(\\d+):( Hotfix for ([^:]*):)?( API change:)?(.*)$", Pattern.DOTALL);
	
	public static char SVN_SERVER_PATH_SEPARATOR = '/';
	
	public static  BufferedReader SYSTEM_IN = new BufferedReader(new InputStreamReader(System.in));

	/** 
	 * @param matcher A {@link Matcher} matching {@link #TICKET_PATTERN}
	 */
	public static String getTicketNumber(Matcher matcher) {
		return matcher.group(1);
	}
	
	/** 
	 * @param matcher A {@link Matcher} matching {@link #TICKET_PATTERN}
	 */
	public static String getApiChange(Matcher matcher) {
		return matcher.group(4);
	}
	
	/** 
	 * @param matcher A {@link Matcher} matching {@link #TICKET_PATTERN}
	 */
	public static boolean isHotfix(Matcher matcher) {
		return matcher.group(2) != null;
	}
	
	/** 
	 * @param matcher A {@link Matcher} matching {@link #TICKET_PATTERN}
	 */
	public static String getHotfixBranch(Matcher matcher) {
		return matcher.group(3);
	}
	
	/** 
	 * @param matcher A {@link Matcher} matching {@link #TICKET_PATTERN}
	 */
	public static String getOriginalMessage(Matcher matcher) {
		return matcher.group(5);
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

