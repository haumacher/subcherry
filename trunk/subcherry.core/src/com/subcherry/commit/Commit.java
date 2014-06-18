package com.subcherry.commit;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

import com.subcherry.CommitConfig;
import com.subcherry.MergeCommitHandler;
import com.subcherry.utils.ArrayUtil;
import com.subcherry.utils.PathParser;
import com.subcherry.utils.Utils.TicketMessage;

/**
 * @version $Revision$ $Author$ $Date$
 */
public class Commit {

	private static final SVNProperties NO_ADDITIONAL_PROPERTIES = null;

	private final CommitConfig _config;

	private final SVNLogEntry _logEntry;

	private String commitMessage;

	private final TicketMessage _ticketMessage;

	private final Set<String> _touchedResources;

	public Commit(CommitConfig config, SVNLogEntry logEntry, TicketMessage ticketMessage) {
		_config = config;
		_logEntry = logEntry;
		_ticketMessage = ticketMessage;
		_touchedResources = new HashSet<>();
	}

	public SVNLogEntry getLogEntry() {
		return _logEntry;
	}

	public TicketMessage getTicketMessage() {
		return _ticketMessage;
	}

	public void join(Commit joinedCommit) {
		setCommitMessage(getCommitMessage() + "\\n" + joinedCommit.getCommitMessage());
		addTouchedResources(joinedCommit.getTouchedResources());
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
		commitPathes.addAll(getAffectedPaths());
		commitPathes.addAll(getTouchedModules());
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
		File[] paths = getTouchedModules().toArray(ArrayUtil.EMPTY_FILE_ARRAY);
		updateClient.doUpdate(paths, revision, depth, allowUnversionedObstructions, depthIsSticky);
	}

	private List<File> getTouchedModules() {
		List<File> files = new ArrayList<File>();
		for (String resource : _touchedResources) {
			String module = PathParser.getModule(resource);
			files.add(new File(getWorkspaceRoot(), module));
		}
		return files;
	}

	@Override
	public String toString() {
		StringBuilder toStringBuilder = new StringBuilder();
		toStringBuilder.append("Commit[");
		toStringBuilder.append("Msg:").append(getCommitMessage());
		toStringBuilder.append(',');
		toStringBuilder.append("Pathes:").append(getAffectedPaths());
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

	public Set<String> getTouchedResources() {
		return _touchedResources;
	}

	public void addTouchedResources(Set<String> touchedResources) {
		_touchedResources.addAll(touchedResources);
	}

	public List<File> getAffectedPaths() {
		ArrayList<File> result = new ArrayList<>(_touchedResources.size());
		for (String resource : _touchedResources) {
			result.add(new File(getWorkspaceRoot(), resource));
		}
		return result;
	}

	public boolean excludeResource(String path) {
		return _touchedResources.remove(toResource(path));
	}

	public boolean includeResource(String path) {
		return _touchedResources.add(toResource(path));
	}

	private String toResource(String path) {
		String root = getWorkspaceRoot().getPath();
		if (path.startsWith(root)) {
			return path.substring(root.length());
		}
		return path;
	}

	private File getWorkspaceRoot() {
		return _config.getWorkspaceRoot();
	}

}
