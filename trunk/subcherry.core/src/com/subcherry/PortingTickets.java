/*
 * TimeCollect records time you spent on your development work.
 * Copyright (C) 2013 Bernhard Haumacher and others
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.subcherry.trac.Ticket;
import com.subcherry.trac.TracConnection;

public class PortingTickets {

	private static final Logger LOG = Logger.getLogger(PortingTickets.class.getName());

	private Map<String, PortType> _additionalTickets;

	public PortingTickets(Configuration config, TracConnection trac) {
		_additionalTickets = getAdditionalTickets(config);
		PortType defaultPortType = getPortType(config);
		Collection<? extends String> queryTickets = getQueryTickets(trac, config);
		for (String queryTicket : queryTickets) {
			_additionalTickets.put(queryTicket, defaultPortType);
		}
	}

	private Map<String, PortType> getAdditionalTickets(Configuration config) {
		String[] additionalTicketSpec = config.getAdditionalTickets();
		Map<String, PortType> result = new HashMap<String, PortType>();
		for (String spec : additionalTicketSpec) {
			Pattern pattern = Pattern.compile("(?:\\#)?(\\d+)(?:\\([^\\)]\\))");
			Matcher matcher = pattern.matcher(spec);
			if (matcher.matches()) {
				String portTypeSpec = matcher.group(2);
				PortType portType = PortType.valueOf(portTypeSpec.toUpperCase());
				if (portType == null) {
					throw new RuntimeException("Invalid port type: " + portTypeSpec);
				}
				result.put(matcher.group(1), portType);
			} else {
				throw new RuntimeException("Invalid additional ticket spec: " + spec);
			}
		}
		return result;
	}

	private Collection<? extends String> getQueryTickets(TracConnection trac, Configuration config) {
		String ticketQuery = config.getTicketQuery();
		if (ticketQuery == null || ticketQuery.trim().isEmpty()) {
			return Collections.emptyList();
		}

		Vector<Integer> ticketIds = trac.getTicket().query(ticketQuery);
		ArrayList<String> result = DefaultLogEntryMatcher.toStringIds(ticketIds);

		LOG.info("Using tickets from query: " + result);

		return result;
	}

	private PortType getPortType(Configuration config) {
		if (config.getRebase()) {
			return PortType.REBASE;
		}
		if (config.getRevert()) {
			return PortType.REVERT;
		}
		if (config.getPreview()) {
			return PortType.PREVIEW;
		}
		return PortType.PORT;
	}

	public boolean shouldPort(Ticket ticket) {
		return _additionalTickets.containsKey(ticket.id());
	}

}
