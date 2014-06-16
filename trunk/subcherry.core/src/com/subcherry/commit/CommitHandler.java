package com.subcherry.commit;

import org.tmatesoft.svn.core.SVNLogEntry;

import com.subcherry.Configuration;
import com.subcherry.merge.Handler;
import com.subcherry.utils.Utils.TicketMessage;

/**
 * @version $Revision$ $Author$ $Date$
 */
public class CommitHandler extends Handler {
	
	static final String TRUNK = "trunk";
	static final String ORIGINAL = "{original}";
	static final String BACKPORT = "{Backport}";
	
	final MessageRewriter _messageRewriter;

	public CommitHandler(Configuration config, MessageRewriter messageRewriter) {
		super(config);
		
		_messageRewriter = messageRewriter;
	}

	public Commit parseCommit(SVNLogEntry logEntry) {
		TicketMessage ticketMessage = new TicketMessage(logEntry.getRevision(), logEntry.getMessage(), _messageRewriter);
		return new Commit(_config, logEntry, ticketMessage);
	}

}
