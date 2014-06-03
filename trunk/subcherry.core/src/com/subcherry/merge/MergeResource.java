package com.subcherry.merge;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import com.subcherry.Configuration;

/**
 * Description of a single native merge operation.
 * 
 * @version $Revision$ $Author$ $Date: 2013-03-16 15:55:27 +0100
 *          (Sat, 16 Mar 2013) $
 */
public class MergeResource {

	private String _targetPath;

	private SVNURL _sourceUrl;

	private final boolean _ignoreAncestry;

	private Configuration _config;

	private long _revision;

	/**
	 * Creates a {@link MergeResource}.
	 * 
	 * @param config
	 *        The tool configuration.
	 * @param revision
	 *        See {@link #getRevision()}.
	 * @param targetPath
	 *        See {@link #getTargetPath()}.
	 * @param urlPrefix
	 *        The SVN url including the source branch.
	 * @param ignoreAncestry
	 *        See {@link #getIgnoreAncestry()}.
	 */
	public MergeResource(Configuration config, long revision, String targetPath, String urlPrefix,
			boolean ignoreAncestry) throws SVNException {
		_config = config;
		_revision = revision;
		_targetPath = targetPath;
		_sourceUrl = SVNURL.parseURIDecoded(urlPrefix + targetPath);
		_ignoreAncestry = ignoreAncestry;
	}

	/**
	 * The source of the merge.
	 */
	public SVNURL getSourceUrl() {
		return _sourceUrl;
	}

	/**
	 * The workspace-relative name of the target of the merge.
	 */
	public String getTargetPath() {
		return _targetPath;
	}
	
	/**
	 * The single revision to merge.
	 */
	public long getRevision() {
		return _revision;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (! (obj instanceof MergeResource)) {
			return false;
		}

		return equalsModule((MergeResource)obj);
	}

	private boolean equalsModule(MergeResource other) {
		return _targetPath.equals(other._targetPath);
	}
	
	@Override
	public int hashCode() {
		return _targetPath.hashCode();
	}

	/**
	 * Whether SVN merge info should be ignored during this merge.
	 */
	public boolean getIgnoreAncestry() {
		return _ignoreAncestry;
	}

	/**
	 * Creates the SVN merge operation.
	 */
	public SvnMerge createMerge(SVNDiffClient diffClient) {
		File targetFile = new File(_config.getWorkspaceRoot(), getTargetPath());

		SvnOperationFactory operationsFactory = diffClient.getOperationsFactory();
		SvnMerge merge = operationsFactory.createMerge();
		boolean revert = _config.getRevert();
		SVNRevision startRevision = SVNRevision.create(revert ? _revision : _revision - 1);
		SVNRevision endRevision = SVNRevision.create(revert ? _revision - 1 : _revision);
		
		SVNDiffOptions mergeOptions = diffClient.getMergeOptions();
		merge.setMergeOptions(mergeOptions);
		/* Must allow as otherwise the whole workspace is checked for revisions which costs much
		 * time */
		boolean allowMixedRevisionsWCForMerge = true; // diffClient.isAllowMixedRevisionsWCForMerge();
		merge.setAllowMixedRevisions(allowMixedRevisionsWCForMerge);
		SvnTarget target = SvnTarget.fromFile(targetFile);
		merge.setSingleTarget(target);
		
		SvnTarget source = SvnTarget.fromURL(getSourceUrl(), startRevision);
		merge.setSource(source, false);
		SvnRevisionRange range = SvnRevisionRange.create(startRevision, endRevision);
		merge.addRevisionRange(range);
		
		merge.setIgnoreAncestry(revert || getIgnoreAncestry());
		return merge;
	}

}

