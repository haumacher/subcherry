package com.subcherry.log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.SVNLogEntry;

public class TicketXRef {
	private Map<Integer, Set<String>> branchesByTicket = new HashMap<Integer, Set<String>>();
	private Map<Integer, List<SVNLogEntry>> commitsByTicket = new HashMap<Integer, List<SVNLogEntry>>();
	private Map<String, List<SVNLogEntry>> invalidCommitsByReason = new HashMap<String, List<SVNLogEntry>>();

	public TicketXRef(Pattern commitMessagePattern, Pattern commitMessageIgnorePattern, Pattern pathPattern, Pattern fileIgnorePattern, Iterable<SVNLogEntry> svnLog) {
		Set<String> branchesBuffer = new HashSet<String>();
		
		for (Iterator<SVNLogEntry> entries = svnLog.iterator(); entries.hasNext();) {
			SVNLogEntry logEntry = entries.next();
			
			boolean isCommitRelevant = false;
			Set<String> changedPaths = logEntry.getChangedPaths().keySet();
			for (String path : changedPaths) {
				Matcher pathMatcher = pathPattern.matcher(path);
				if (pathMatcher.matches()) {
					String file = pathMatcher.group(2);
					if (! fileIgnorePattern.matcher(file).matches()) {
						isCommitRelevant = true;
						
						String branch = pathMatcher.group(1);
						branchesBuffer.add(branch);
					}
				}
			}
			
			if (isCommitRelevant) {
				String message = logEntry.getMessage();
				Matcher messageMatcher = commitMessagePattern.matcher(message);
	
				if (messageMatcher.matches()) {
					Integer ticketId = Integer.parseInt(messageMatcher.group(1));
	
					// Add branches to ticket.
					addAllSet(branchesByTicket, ticketId, branchesBuffer);
	
					// Add commit to ticket.
					addList(commitsByTicket, ticketId, logEntry);
				} else {
					if (! commitMessageIgnorePattern.matcher(message).matches()) {
						addList(invalidCommitsByReason, "No ticket ID in commit message", logEntry);
					}
				}
			}
			
			branchesBuffer.clear();
		}
	}

	public static <K, V> void addSet(Map<K, Set<V>> multiMap, K key, V values) {
		Set<V> branches = multiMap.get(key);
		if (branches == null) {
			branches = new HashSet<V>();
			multiMap.put(key, branches);
		}
		branches.add(values);
	}

	public static <K, V> void addAllSet(Map<K, Set<V>> multiMap, K key, Collection<V> values) {
		Set<V> branches = multiMap.get(key);
		if (branches == null) {
			branches = new HashSet<V>(values);
			multiMap.put(key, branches);
		} else {
			branches.addAll(values);
		}
	}
	
	public static <K, V> List<V> addList(Map<K, List<V>> multiMap, K key, V value) {
		List<V> commits = multiMap.get(key);
		if (commits == null) {
			commits = new ArrayList<V>();
			multiMap.put(key, commits);
		}
		commits.add(value);
		return commits;
	}

	public Set<String> getCommitBranches(Integer ticketId) {
		return branchesByTicket.get(ticketId);
	}

	public List<SVNLogEntry> getCommits(Integer ticketId) {
		return commitsByTicket.get(ticketId);
	}

	public Set<Integer> getAllTicketIds() {
		return commitsByTicket.keySet();
	}
	
	public Map<String, List<SVNLogEntry>> getInvalidCommitsByReason() {
		return invalidCommitsByReason;
	}
}