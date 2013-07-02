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
package com.subcherry.commit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.SVNLogEntry;

import com.subcherry.Configuration;
import com.subcherry.PortType;
import com.subcherry.PortingTickets;
import com.subcherry.utils.Utils;
import com.subcherry.utils.Utils.TicketMessage;

public class MessageRewriter {

	private final Configuration _config;

	private final PortingTickets _portingTickets;

	public MessageRewriter(Configuration config, PortingTickets portingTickets) {
		_config = config;
		_portingTickets = portingTickets;
	}

	public String resolvePortMessage(SVNLogEntry logEntry) {
		String logEntryMessage = logEntry.getMessage();
		if (CommitHandler.ORIGINAL.equals(_config.getPortMessage())) {
			return logEntryMessage; 
		}
		if (CommitHandler.BACKPORT.equals(_config.getPortMessage())) {
			return backPortMessage(logEntryMessage);
		}
		
		TicketMessage message = new TicketMessage(logEntryMessage);
	
		StringBuilder newMesssage = new StringBuilder();
		newMesssage.append("Ticket #");
		newMesssage.append(message.ticketNumber);
		newMesssage.append(": ");
	
		if (shouldRebase(message.ticketNumber)) {
			if (message.isHotfix()) {
				addHotfix(newMesssage);
			} else if (message.isPreview()) {
				addPreview(newMesssage);
			} else if (message.isBranchChange()) {
				addBranchChange(newMesssage);
			} else if (message.isPort()) {
				addPort(newMesssage);
			}
		}
		else if (shouldHotfix(message.ticketNumber)) {
			addHotfix(newMesssage);
		}
		else if (shouldPreview(message.ticketNumber)) {
			addPreview(newMesssage);
		} 
		else {
			if (!shouldRevert(message.ticketNumber)) {
				addPort(newMesssage);
			}
		}
	
		if (message.apiChange != null) {
			newMesssage.append("API change: ");
		}
		
		if (shouldRevert(message.ticketNumber)) {
			newMesssage.append("Reverted ");
		}
		newMesssage.append("[");
		newMesssage.append(logEntry.getRevision());
		newMesssage.append("]:");
		
		newMesssage.append(message.originalMessage);
		return newMesssage.toString();
	}

	private boolean shouldRevert(String ticketNumber) {
		return getPortType(ticketNumber) == PortType.REVERT;
	}

	private boolean shouldPreview(String ticketNumber) {
		return getPortType(ticketNumber) == PortType.PREVIEW;
	}

	private boolean shouldHotfix(String ticketNumber) {
		return getPortType(ticketNumber) == PortType.HOTFIX;
	}

	private boolean shouldRebase(String ticketNumber) {
		return getPortType(ticketNumber) == PortType.REBASE;
	}

	private PortType getPortType(String ticketNumber) {
		return _portingTickets.getPortType(ticketNumber);
	}

	private String backPortMessage(String logEntryMessage) {
		String branchName = getBranchName(_config.getSourceBranch());
		Pattern pattern = Pattern.compile("Ticket #(\\d+): On " + branchName + ": (.*)", Pattern.DOTALL);
		Matcher matcher = pattern.matcher(logEntryMessage);
		if (!matcher.matches()) {
			throw new IllegalStateException();
		}
		String ticketNumber = matcher.group(1);
		String originalMessage = matcher.group(2);
		
		StringBuilder backportMessage = new StringBuilder("Ticket #");
		backportMessage.append(ticketNumber);
		backportMessage.append(": ");
		
		String targetBranch = getBranchName(_config.getTargetBranch());
		if (!isTrunk(targetBranch)) {
			backportMessage.append("On ");
			backportMessage.append(targetBranch);
			backportMessage.append(": ");
		}
		backportMessage.append(originalMessage);
		return backportMessage.toString();
	}

	private void addHotfix(StringBuilder newMesssage) {
		newMesssage.append("Hotfix for ");
		newMesssage.append(getBranchName(_config.getTargetBranch()));
		newMesssage.append(": ");
	}

	private void addPort(StringBuilder newMesssage) {
		newMesssage.append("Ported to ");
		newMesssage.append(getBranchName(_config.getTargetBranch()));
		newMesssage.append(" from ");
		newMesssage.append(getBranchName(_config.getSourceBranch()));
		newMesssage.append(": ");
	}

	private void addPreview(StringBuilder newMesssage) {
		newMesssage.append("Preview on ");
		newMesssage.append(getBranchName(_config.getTargetBranch()));
		newMesssage.append(": ");
	}

	private void addBranchChange(StringBuilder newMesssage) {
		newMesssage.append("On ");
		newMesssage.append(getBranchName(_config.getTargetBranch()));
		newMesssage.append(": ");
	}

	private String getBranchName(String branch) {
		Pattern trunkPattern = Pattern.compile("^/?(" + CommitHandler.TRUNK +")(?:/([^/]+))$");
		Matcher trunkMatcher = trunkPattern.matcher(branch);
		if (trunkMatcher.matches()) {
			String category = trunkMatcher.group(2);
			return (category != null ? category + "_" : "") + CommitHandler.TRUNK;
		}
		int index = branch.lastIndexOf(Utils.SVN_SERVER_PATH_SEPARATOR);
		if (index == -1) {
			return branch;
		} else {
			return branch.substring(index +1);
		}
	}

	private boolean isTrunk(String branchName) {
		return branchName.endsWith(CommitHandler.TRUNK);
	}

}