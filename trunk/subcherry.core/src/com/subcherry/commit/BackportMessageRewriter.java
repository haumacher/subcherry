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

import com.subcherry.Configuration;
import com.subcherry.PortingTickets;
import com.subcherry.utils.Utils.TicketMessage;

public class BackportMessageRewriter extends MessageRewriter {

	protected BackportMessageRewriter(Configuration config, PortingTickets portingTickets) {
		super(config, portingTickets);
	}

	@Override
	public String getMergeMessage(long originalRevision, TicketMessage message) {
		return backPortMessage(message.getLogEntryMessage());
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

}
