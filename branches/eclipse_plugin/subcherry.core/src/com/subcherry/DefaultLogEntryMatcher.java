package com.subcherry;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
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
	private Set<String> _additionalTickets;
	private Collection<String> _milestones;

	public DefaultLogEntryMatcher(LoginCredential tracCredentials, Configuration config) throws MalformedURLException {
		_trac = new TracConnection(config.getTracURL(), tracCredentials.getUser(), tracCredentials.getPasswd());
		
		_ignoreRevisions = getIgnoreRevisions(config);
		_additionalRevisions = getAdditionalRevisions(config);
		_additionalTickets = getAdditionalTickets(config);
		_milestones = getMilestones(config);
		
		_additionalTickets.addAll(getQueryTickets(config));
	}
	
	private Collection<? extends String> getQueryTickets(Configuration config) {
		String ticketQuery = config.getTicketQuery();
		if (ticketQuery == null || ticketQuery.trim().isEmpty()) {
			return Collections.emptyList();
		}
		
		
		Vector<Integer> ticketIds = _trac.getTicket().query(ticketQuery);
		ArrayList<String> result = toStringIds(ticketIds);
		
		LOG.info("Using tickets from query: " + result);
		
		return result;
	}

	public static ArrayList<String> toStringIds(Collection<Integer> ticketIds) {
		ArrayList<String> result = new ArrayList<String>();
		for (Integer id : ticketIds) {
			result.add(Integer.toString(id));
		}
		return result;
	}

	private Set<String> getAdditionalTickets(Configuration config) {
		Set<String> revisions = new HashSet<String>();
		String[] configuredTickets = config.getAdditionalTickets();
		Collections.addAll(revisions, configuredTickets);
		return revisions;
	}

	private Set<Long> getAdditionalRevisions(Configuration config) {
		Set<Long> revisions = new HashSet<Long>();
		Long[] configuredRevisions = config.getAdditionalRevisions();
		Collections.addAll(revisions, configuredRevisions);
		return revisions;
	}

	private Collection<String> getMilestones(Configuration config) {
		Set<String> milestones = new HashSet<String>();
		String targetMilestone = config.getTargetMilestone();
		if (targetMilestone != null) {
			milestones.add(targetMilestone);
		}
		Collections.addAll(milestones, config.getMilestones());
		return milestones;
	}

	private static Set<Long> getIgnoreRevisions(Configuration config) {
		Set<Long> revisions = new HashSet<Long>();
		Long[] configuredRevisions = config.getIgnoreRevisions();
		Collections.addAll(revisions, configuredRevisions);
		return revisions;
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
		if (_additionalTickets.contains(ticket.id())) {
			return true;
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

