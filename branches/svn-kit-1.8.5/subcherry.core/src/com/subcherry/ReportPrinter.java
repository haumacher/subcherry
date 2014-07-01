/*
 * TimeCollect records time you spent on your development work.
 * Copyright (C) 2014 Bernhard Haumacher and others
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

import com.subcherry.history.Change;
import com.subcherry.history.Node;
import com.subcherry.trac.TracTicket;

public class ReportPrinter {
	private String _ticketId;

	private TracTicket _ticket;

	private Change _missingChange;

	private Node _conflictNode;

	private boolean _ticketPrinted = true;

	private boolean _missingChangePrinted = true;

	private boolean _conflictNodePrinted = true;

	private int _conflicts;

	private int _conflictsPerTicket;

	public void startReport() {
		System.out.println("= Conflict Report =");
	
		_conflicts = 0;
	}

	public void setTicket(String ticketId, TracTicket ticket) {
		_ticketId = ticketId;
		_ticket = ticket;

		_ticketPrinted = false;
		_conflictsPerTicket = 0;
	}

	public void setMissingChange(Change missingChange) {
		_missingChange = missingChange;
	
		_missingChangePrinted = false;
	}

	public void setConflictNode(Node conflictNode) {
		_conflictNode = conflictNode;
	
		_conflictNodePrinted = false;
	}

	public void printConflictingChange(Change conflict) {
		printTicket();
		printMissingChange();
		printConflictNode();
	
		System.out.println("    * [" + conflict.getRevision() + "]: "
			+ quote(conflict.getMessage())
			+ " (" + conflict.getAuthor() + ")");
	
		_conflicts++;
		_conflictsPerTicket++;
	}

	public void endTicket() {
		if (_conflictsPerTicket > 0) {
			System.out.println();
		}
	}

	public void endReport() {
		if (!hasConflictsReported()) {
			System.out.println("No conflicts detected.");
		}
	}

	public boolean hasConflictsReported() {
		return _conflicts > 0;
	}

	private void printTicket() {
		if (_ticketPrinted) {
			return;
		} else {
			_ticketPrinted = true;
		}

		if (_ticket == null) {
			System.out.println("== Without ticket ==");
		} else {
			boolean hasComponent = _ticket.getComponent() != null && !_ticket.getComponent().isEmpty();
			boolean hasMilestone = _ticket.getMilestone() != null && !_ticket.getMilestone().isEmpty();
			System.out.println("== Ticket #"
				+ _ticketId + " "
				+ (hasComponent ? _ticket.getComponent() : "-")
				+ (hasMilestone ? "/" + _ticket.getMilestone() : "")
				+ ": "
				+ _ticket.getSummary() + " ("
				+ _ticket.getStatus()
				+ (_ticket.getResolution() != null && !_ticket.getResolution().isEmpty() ? "/"
					+ _ticket.getResolution() : "")
				+ ") ==");
		}
	}

	private void printMissingChange() {
		if (_missingChangePrinted) {
			return;
		} else {
			_missingChangePrinted = true;
		}

		System.out.println("[" + _missingChange.getRevision() + "]: " + quote(_missingChange.getMessage())
			+ " (" + _missingChange.getAuthor() + ")");
	}

	private void printConflictNode() {
		if (_conflictNodePrinted) {
			return;
		} else {
			_conflictNodePrinted = true;
		}

		System.out.println(" * " + _conflictNode.getPath()
			+ (_conflictNode.isAlive() ? "" : " (deleted in [" + (_conflictNode.getRevMax() + 1) + "])"));
	}

	private static String quote(String message) {
		return message.trim().replaceAll("[\\r\\n]\\s*|\\s\\s+", " ");
	}

}
