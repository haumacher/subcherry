package com.subcherry.trac;

import java.util.HashMap;
import java.util.Map;

import com.subcherry.utils.Utils;

public class TicketStub extends Ticket {

	private static final Ticket NO_TICKET = new Ticket() {
		
		@Override
		public String type() {
			return null;
		}
		
		@Override
		public String title() {
			return null;
		}
		
		@Override
		public String status() {
			return null;
		}
		
		@Override
		public String resolution() {
			return null;
		}
		
		@Override
		public String milestone() {
			return null;
		}
		
		@Override
		public String implementedIn() {
			return null;
		}
		
		@Override
		public String id() {
			return null;
		}
		
		@Override
		public String component() {
			return null;
		}
	};
	
	protected final String _id;
	private TracTicket tracTicket;
	private final TracConnection _trac;

	private TicketStub(String ticketId, TracConnection trac) {
		_id = ticketId;
		_trac = trac;
	}
	
	@Override
	public String id() {
		return _id;
	}

	@Override
	public String title() {
		return (String) tracTicket().getAttributeValue(TracTicket.TICKET_ATT_SUMMARY);
	}

	@Override
	public String component() {
		return (String) tracTicket().getAttributeValue(TracTicket.TICKET_ATT_COMPONENT);
	}

	@Override
	public String type() {
		return (String) tracTicket().getAttributeValue(TracTicket.TICKET_ATT_TYPE);
	}

	@Override
	public String status() {
		return (String) tracTicket().getAttributeValue(TracTicket.TICKET_ATT_STATUS);
	}

	@Override
	public String resolution() {
		return (String) tracTicket().getAttributeValue(TracTicket.TICKET_ATT_RESOLUTION);
	}

	@Override
	public String milestone() {
		return (String) tracTicket().getAttributeValue(TracTicket.TICKET_ATT_MILESTONE);
	}

	@Override
	public String implementedIn() {
		return (String) tracTicket().getAttributeValue(TracTicket.TICKET_ATT_IMPLEMENTED_IN);
	}

	public static Ticket getTicket(TracConnection trac, String message) {
		String ticketId = Utils.getTicketId(message);
		if (ticketId != null) {
			return fetchTicket(trac, message, ticketId);
		} else {
			return NO_TICKET;
		}
	}

	private static Ticket fetchTicket(TracConnection trac, String message, String ticketId) {
		Ticket cachedTicket = tickets.get(ticketId);
		if (cachedTicket != null) {
			return cachedTicket;
		}
		
		Ticket newTicket = new TicketStub(ticketId, trac);
		tickets.put(message, newTicket);
		return newTicket;
	}

	private static Map<Integer, TracTicket> tracTickets = new HashMap<Integer, TracTicket>();
	private static Map<String, Ticket> tickets = new HashMap<String, Ticket>();

	public static TracTicket getTicket(TracConnection trac, int ticketNumber) {
		TracTicket result = tracTickets.get(ticketNumber);
		if (result == null) {
			result = TracTicket.getTicket(trac, ticketNumber);
			tracTickets.put(ticketNumber, result);
		}
		return result;
	}

	private TracTicket tracTicket() {
		if (tracTicket == null) {
			int ticketNumber = Integer.parseInt(this._id);
			this.tracTicket = getTicket(_trac, ticketNumber);
		}
		return tracTicket;
	}

}
