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
import com.subcherry.PortType;
import com.subcherry.PortingTickets;
import com.subcherry.utils.Utils;
import com.subcherry.utils.Utils.TicketMessage;

public class MessageRewriter {

	private static final Pattern REVISION_REF_PATTERN = Pattern.compile("\\[(\\d+)\\]");

	public static MessageRewriter createMessageRewriter(Configuration config, PortingTickets portingTickets,
			RevisionRewriter revisionRewriter) {
		if (CommitHandler.ORIGINAL.equals(config.getPortMessage())) {
			return new NoMessageRewrite(config, portingTickets);
		}
		if (CommitHandler.BACKPORT.equals(config.getPortMessage())) {
			return new BackportMessageRewriter(config, portingTickets);
		}
		return new MessageRewriter(config, portingTickets, revisionRewriter);
	}

	protected final Configuration _config;

	private final PortingTickets _portingTickets;
	
	private final RevisionRewriter _revisionRewriter;

	protected MessageRewriter(Configuration config, PortingTickets portingTickets, RevisionRewriter revisionRewriter) {
		_config = config;
		_portingTickets = portingTickets;
		_revisionRewriter = revisionRewriter;
	}

	public String getMergeMessage(TicketMessage message) {
		StringBuilder newMesssage = new StringBuilder();

		appendTicketPrefix(newMesssage, message);
		appendPortingTypeModifier(newMesssage, message);
		appendApiChangeModifier(newMesssage, message);
		appendFollowUpModifier(newMesssage, message);
		appendOriginalRevisionModifier(newMesssage, message);
		appendOriginalMessage(newMesssage, message);

		return newMesssage.toString();
	}

	protected void appendTicketPrefix(StringBuilder newMesssage, TicketMessage message) {
		newMesssage.append("Ticket #");
		newMesssage.append(message.ticketNumber);
		newMesssage.append(": ");
	}

	protected void appendPortingTypeModifier(StringBuilder newMesssage, TicketMessage oldMessage) {
		if (shouldRebase(oldMessage.ticketNumber)) {
			if (oldMessage.isHotfix()) {
				addHotfix(newMesssage);
			} else if (oldMessage.isPreview()) {
				addPreview(newMesssage);
			} else if (oldMessage.isBranchChange()) {
				addBranchChange(newMesssage);
			} else if (oldMessage.isPort()) {
				addPort(oldMessage, newMesssage);
			}
		}
		else if (shouldHotfix(oldMessage.ticketNumber)) {
			addHotfix(newMesssage);
		}
		else if (shouldPreview(oldMessage.ticketNumber)) {
			addPreview(newMesssage);
		} 
		else {
			if (!shouldRevert(oldMessage.ticketNumber) && !shouldReintegrate(oldMessage.ticketNumber)) {
				addPort(oldMessage, newMesssage);
			}
			if (shouldReintegrate(oldMessage.ticketNumber) && !Utils.mergeToTrunk(_config)) {
				addBranchChange(newMesssage);
			}
		}
	}

	protected void appendApiChangeModifier(StringBuilder newMesssage, TicketMessage message) {
		if (message.apiChange != null) {
			newMesssage.append("API change: ");
		}
	}

	protected void appendFollowUpModifier(StringBuilder newMesssage, TicketMessage message) {
		if (message.getLeadRevision() > 0) {
			long rewrittenLeadRevision = _revisionRewriter.rewrite(message.getLeadRevision());
			if (rewrittenLeadRevision > 0) {
				newMesssage.append("Follow-up for [" + rewrittenLeadRevision + "]: ");
			}
		}
	}

	protected void appendOriginalRevisionModifier(StringBuilder newMesssage, TicketMessage message) {
		if (shouldRevert(message.ticketNumber)) {
			newMesssage.append("Reverted ");
		}
		newMesssage.append("[");
		if (_config.getSilentRebase()) {
			String mergedRevision = message.getMergedRevision();
			if (mergedRevision != null) {
				newMesssage.append(mergedRevision);
			} else {
				newMesssage.append(message.getOriginalRevision());
			}
		} else {
			newMesssage.append(message.getOriginalRevision());
		}
		newMesssage.append("]:");
	}

	protected void appendOriginalMessage(StringBuilder newMesssage, TicketMessage message) {
		String original = message.originalMessage;
		Matcher matcher = REVISION_REF_PATTERN.matcher(original);
		int afterLastMatch = 0;
		while (matcher.find()) {
			long rewrittenRev = Long.parseLong(matcher.group(1));
			if (rewrittenRev > 0) {
				newMesssage.append(original.subSequence(afterLastMatch, matcher.start()));
				newMesssage.append('[');
				newMesssage.append(_revisionRewriter.rewrite(rewrittenRev));
				newMesssage.append(']');
				afterLastMatch = matcher.end();
			}
		}
		newMesssage.append(original.substring(afterLastMatch));
	}

	private boolean shouldRevert(String ticketNumber) {
		return _config.getRevert();
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

	private boolean shouldReintegrate(String ticketNumber) {
		return getPortType(ticketNumber) == PortType.REINTEGRATE;
	}

	private PortType getPortType(String ticketNumber) {
		return _portingTickets.getPortType(ticketNumber);
	}

	private void addHotfix(StringBuilder newMesssage) {
		newMesssage.append("Hotfix for ");
		newMesssage.append(getBranchName(_config.getTargetBranch()));
		newMesssage.append(": ");
	}

	private void addPort(TicketMessage oldMessage, StringBuilder newMesssage) {
		newMesssage.append("Ported to ");
		newMesssage.append(getBranchName(_config.getTargetBranch()));
		newMesssage.append(" from ");
		newMesssage.append(getSourceBranch(oldMessage));
		newMesssage.append(": ");
	}

	private String getSourceBranch(TicketMessage oldMessage) {
		String sourceBranch = null;
		if (_config.getSilentRebase()) {
			sourceBranch = oldMessage.getSourceBranch();
		}

		if (sourceBranch == null) {
			sourceBranch = getBranchName(_config.getSourceBranch());
		}
		return sourceBranch;
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

	protected final String getBranchName(String branch) {
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

	protected final boolean isTrunk(String branchName) {
		return branchName.endsWith(CommitHandler.TRUNK);
	}

}
