package com.subcherry.commit;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

import com.subcherry.MergeCommitHandler;
import com.subcherry.utils.ArrayUtil;
import com.subcherry.utils.Utils.TicketMessage;

/**
 * @version $Revision$ $Author$ $Date$
 */
public class Commit {

	private static final SVNProperties NO_ADDITIONAL_PROPERTIES = null;

	private final SVNLogEntry _logEntry;

	private Set<File> _touchedModules;

	private String commitMessage;

	private File[] _affectedPaths;

	private final TicketMessage _ticketMessage;

	public Commit(SVNLogEntry logEntry, Set<File> touchedModules, TicketMessage ticketMessage, File[] affectedPaths) {
		_logEntry = logEntry;
		_touchedModules = touchedModules;
		_ticketMessage = ticketMessage;
		_affectedPaths = affectedPaths;
	}

	public SVNLogEntry getLogEntry() {
		return _logEntry;
	}

	public TicketMessage getTicketMessage() {
		return _ticketMessage;
	}

	public void join(Commit joinedCommit) {
		_touchedModules.addAll(joinedCommit._touchedModules);
		setCommitMessage(getCommitMessage() + "\\n" + joinedCommit.getCommitMessage());
		setAffectedPaths(join(getAffectedPaths(), joinedCommit.getAffectedPaths()));
	}

	private File[] join(File[] paths1, File[] paths2) {
		HashSet<File> buffer = new HashSet<File>();
		buffer.addAll(Arrays.asList(paths1));
		buffer.addAll(Arrays.asList(paths2));
		return buffer.toArray(new File[buffer.size()]);
	}

	public SVNCommitInfo run(CommitContext context) throws SVNException {
		SVNCommitInfo commitInfo = doCommit(context.commitClient);
		updateToHEAD(context.updateClient);
		return commitInfo;
	}

	SVNCommitInfo doCommit(SVNCommitClient commitClient) throws SVNException {
		HashSet<File> commitPathes = new HashSet<File>();
		Collections.addAll(commitPathes, getAffectedPaths());
		commitPathes.addAll(_touchedModules);
		boolean keepLocks = false;
		SVNProperties revisionProperties = NO_ADDITIONAL_PROPERTIES;
		String[] changelists = null;
		boolean keepChangelist = false;
		boolean force = false;
		SVNDepth depth = SVNDepth.EMPTY; // all pathes are given explicitly
		SVNCommitInfo commitInfo =
			commitClient.doCommit(commitPathes.toArray(ArrayUtil.EMPTY_FILE_ARRAY), keepLocks, getCommitMessage(),
				revisionProperties, changelists, keepChangelist, force, depth);
		return commitInfo;
	}

	void updateToHEAD(SVNUpdateClient updateClient) throws SVNException {
		SVNRevision revision = SVNRevision.HEAD;
		SVNDepth depth = SVNDepth.INFINITY;
		boolean depthIsSticky = false;
		boolean allowUnversionedObstructions = false;
		File[] paths = _touchedModules.toArray(ArrayUtil.EMPTY_FILE_ARRAY);
		updateClient.doUpdate(paths, revision, depth, allowUnversionedObstructions, depthIsSticky);
	}

	@Override
	public String toString() {
		StringBuilder toStringBuilder = new StringBuilder();
		toStringBuilder.append("Commit[");
		toStringBuilder.append("Msg:").append(getCommitMessage());
		toStringBuilder.append(',');
		toStringBuilder.append("Pathes:").append(Arrays.toString(getAffectedPaths()));
		toStringBuilder.append("]");
		return toStringBuilder.toString();
	}

	public String getDescription() {
		return "[" + getLogEntry().getRevision() + "]: " + MergeCommitHandler.encode(getLogEntry().getMessage());
	}

	public long getRevision() {
		return getLogEntry().getRevision();
	}

	public long getFollowUpForRevison() {
		return _ticketMessage.getLeadRevision();
	}

	public String getCommitMessage() {
		if (commitMessage == null) {
			commitMessage = _ticketMessage.getMergeMessage();
		}
		return commitMessage;
	}

	public void setCommitMessage(String commitMessage) {
		this.commitMessage = commitMessage;
	}

	public File[] getAffectedPaths() {
		return _affectedPaths;
	}

	public void setAffectedPaths(File[] affectedPaths) {
		_affectedPaths = affectedPaths;
	}

}
