package com.subcherry.merge;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
 * @version    $Revision$  $Author$  $Date$
 */
public class Merge {
	
	public long revision;
	public Collection<MergeResource> resources;

	private final boolean revert;

	public Merge(long revision, Collection<MergeResource> resources, boolean revert) {
		this.revision = revision;
		this.resources = resources;
		this.revert = revert;
	}

	public Map<File, List<SVNConflictDescription>> run(MergeContext context) throws SVNException {
		File workspaceRoot = context.config.getWorkspaceRoot();
		
		Map<File, List<SVNConflictDescription>> allConflicts = Collections.emptyMap();
		for (MergeResource resource : this.resources) {
			SVNURL startURL = resource.getURL();
			File dstPath = new File(workspaceRoot, resource.getName());

			Map<File, List<SVNConflictDescription>> moduleConflicts =
				doMerge(context.diffClient, startURL, dstPath, resource.getIgnoreAncestry());
			if (allConflicts.isEmpty()) {
				// allConflicts is unmodifiable.
				allConflicts = moduleConflicts;
			} else {
				allConflicts.putAll(moduleConflicts);
			}
		}
		return allConflicts;
	}
	
	Map<File, List<SVNConflictDescription>> doMerge(SVNDiffClient diffClient, SVNURL url, File dstPath,
			boolean ignoreAncestry) throws SVNException {
		SvnOperationFactory operationsFactory = diffClient.getOperationsFactory();
		SvnMerge merge = operationsFactory.createMerge();
		SVNRevision startRevision = SVNRevision.create(revert ? revision : revision - 1);
		SVNRevision endRevision = SVNRevision.create(revert ? revision - 1 : revision);

		SVNDiffOptions mergeOptions = diffClient.getMergeOptions();
		merge.setMergeOptions(mergeOptions);
		/*
		 * Must allow as otherwise the whole workspace is checked for revisions
		 * which costs much time
		 */
		boolean allowMixedRevisionsWCForMerge = true; // diffClient.isAllowMixedRevisionsWCForMerge();
		merge.setAllowMixedRevisions(allowMixedRevisionsWCForMerge);
		SvnTarget target = SvnTarget.fromFile(dstPath);
		merge.setSingleTarget(target);

		SvnTarget source = SvnTarget.fromURL(url, startRevision);
		merge.setSource(source, false);
		SVNRevisionRange range = new SVNRevisionRange(startRevision, endRevision);
		merge.addRevisionRange(SvnRevisionRange.create(range.getStartRevision(), range.getEndRevision()));
		
		merge.setIgnoreAncestry(revert || ignoreAncestry);
		
		System.out.println("svn merge " + (revert ? "--ignore-ancestry ": "") + "-r" + range.getStartRevision() + ":" + range.getEndRevision() + " " + source + " " + target.getFile());
		
		return execute(merge);
	}

	private Map<File, List<SVNConflictDescription>> execute(SvnOperation<?> op) throws SVNException {
		SvnOperationFactory operationFactory = op.getOperationFactory();

		TouchCollector touchedFilesHandler = new TouchCollector(operationFactory.getEventHandler());
		operationFactory.setEventHandler(touchedFilesHandler);
		try {
			MergeConflictCollector conclictCollector =
				new MergeConflictCollector(
					operationFactory.getOperationHandler(),
					touchedFilesHandler.getTouchedFiles());
			operationFactory.setOperationHandler(conclictCollector);
			try {
				op.run();
				return conclictCollector.getMergeConflicts();
			} finally {
				operationFactory.setOperationHandler(conclictCollector.getDelegate());
			}
		} finally {
			operationFactory.setEventHandler(touchedFilesHandler.getDelegate());
		}
	}


}
