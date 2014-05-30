package com.subcherry;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.tmatesoft.svn.core.SVNLogEntry;

import com.subcherry.trac.Ticket;
import com.subcherry.trac.TicketStub;
import com.subcherry.trac.TracConnection;
import com.subcherry.utils.Log;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class DefaultLogEntryMatcher extends SVNLogEntryMatcher {

	private static final Logger LOG = Logger.getLogger(DefaultLogEntryMatcher.class.getName());
	
	private TracConnection _trac;
	
	private Set<Long> _ignoreRevisions;
	private Set<Long> _additionalRevisions;

	private final PortingTickets _portingTickets;

	private Set<String> _ignoreTickets;
	private Collection<String> _milestones;

	public DefaultLogEntryMatcher(TracConnection trac, Configuration config, PortingTickets portingTickets) throws MalformedURLException {
		_trac = trac;
		
		_ignoreRevisions = getIgnoreRevisions(config);
		_additionalRevisions = getAdditionalRevisions(config);
		_portingTickets = portingTickets;
		_ignoreTickets = getIgnoreTickets(config);
		_milestones = getMilestones(config);
	}
	
	public static ArrayList<String> toStringIds(Collection<Integer> ticketIds) {
		ArrayList<String> result = new ArrayList<String>();
		for (Integer id : ticketIds) {
			result.add(Integer.toString(id));
		}
		return result;
	}

	private Set<String> getIgnoreTickets(Configuration config) {
		return toSet(config.getIgnoreTickets());
	}

	private Set<Long> getAdditionalRevisions(Configuration config) {
		return toSet(config.getAdditionalRevisions());
	}

	private Collection<String> getMilestones(Configuration config) {
		Set<String> milestones = toSet(config.getMilestones());
		String targetMilestone = config.getTargetMilestone();
		if (targetMilestone != null) {
			milestones.add(targetMilestone);
		}
		return milestones;
	}

	private static Set<Long> getIgnoreRevisions(Configuration config) {
		return toSet(config.getIgnoreRevisions());
	}

	private static <T> Set<T> toSet(T[] array) {
		Set<T> set = new HashSet<T>();
		Collections.addAll(set, array);
		return set;
	}


	@Override
	public boolean matches(SVNLogEntry logEntry) {
		long revision = logEntry.getRevision();
		if (_ignoreRevisions.contains(revision)) {
			Log.info("Ignore revision " + revision + " as it is configured to be ignored");
			return false;
		}
		
		if (_additionalRevisions.contains(revision)) {
			Log.info("Use " + revision + " as it is configured to be used");
			return true;
		}

		String message = logEntry.getMessage();
		Ticket ticket = TicketStub.getTicket(_trac, message);

		boolean accept = portByTicket(ticket);
		if (accept) {
			Log.info("Using [" + revision + "] from #" + ticket.id());
		}
		return accept;
	}

	private boolean portByTicket(Ticket ticket) {
		if (_portingTickets.shouldPort(ticket)) {
			return true;
		}
		if (_ignoreTickets.contains(ticket.id())) {
			return false;
		}
		
		if (!_milestones.isEmpty()) {
			if ("closed".equals(ticket.status())) {
				String milestone = ticket.milestone();
				if (milestone != null && _milestones.contains(milestone)) {
					return true;
				}
			}
		}
		return false;
	}

}

