package com.subcherry.commit;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

import com.subcherry.MergeCommitHandler;
import com.subcherry.utils.ArrayUtil;

/**
 * @version $Revision$ $Author$ $Date$
 */
public class Commit {

	private static final SVNProperties NO_ADDITIONAL_PROPERTIES = null;

	private final SVNLogEntry _logEntry;

	public Set<File> touchedModules;
	public String commitMessage;
	public File[] affectedPaths;

	public Commit(SVNLogEntry logEntry, Set<File> touchedModules, String commitMessage, File[] affectedPaths) {
		_logEntry = logEntry;
		this.touchedModules = touchedModules;
		this.commitMessage = commitMessage;
		this.affectedPaths = affectedPaths;
	}

	public SVNLogEntry getLogEntry() {
		return _logEntry;
	}

	public void join(Commit joinedCommit) {
		touchedModules.addAll(joinedCommit.touchedModules);
		commitMessage = commitMessage + "\\n" + joinedCommit.commitMessage;
		affectedPaths = join(affectedPaths, joinedCommit.affectedPaths);
	}

	private File[] join(File[] paths1, File[] paths2) {
		HashSet<File> buffer = new HashSet<File>();
		buffer.addAll(Arrays.asList(paths1));
		buffer.addAll(Arrays.asList(paths2));
		return buffer.toArray(new File[buffer.size()]);
	}

	public void run(CommitContext context) throws SVNException {
		doCommit(context.commitClient);
		updateToHEAD(context.updateClient);
	}

	void doCommit(SVNCommitClient commitClient) throws SVNException {
		HashSet<File> commitPathes = new HashSet<File>();
		Collections.addAll(commitPathes, affectedPaths);
		commitPathes.addAll(touchedModules);
		boolean keepLocks = false;
		SVNProperties revisionProperties = NO_ADDITIONAL_PROPERTIES;
		String[] changelists = null;
		boolean keepChangelist = false;
		boolean force = false;
		SVNDepth depth = SVNDepth.EMPTY; // all pathes are given explicitly
		commitClient.doCommit(commitPathes.toArray(ArrayUtil.EMPTY_FILE_ARRAY), keepLocks, commitMessage,
				revisionProperties, changelists, keepChangelist, force, depth);
	}

	void updateToHEAD(SVNUpdateClient updateClient) throws SVNException {
		SVNRevision revision = SVNRevision.HEAD;
		SVNDepth depth = SVNDepth.INFINITY;
		boolean depthIsSticky = false;
		boolean allowUnversionedObstructions = false;
		File[] paths = touchedModules.toArray(ArrayUtil.EMPTY_FILE_ARRAY);
		updateClient.doUpdate(paths, revision, depth, allowUnversionedObstructions, depthIsSticky);
	}

	@Override
	public String toString() {
		StringBuilder toStringBuilder = new StringBuilder();
		toStringBuilder.append("Commit[");
		toStringBuilder.append("Msg:").append(commitMessage);
		toStringBuilder.append(',');
		toStringBuilder.append("Pathes:").append(Arrays.toString(affectedPaths));
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
		return 0;
	}

}
