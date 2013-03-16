package com.subcherry.merge;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictResult;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import com.subcherry.utils.Log;

/**
 * @version    $Revision$  $Author$  $Date$
 */
public class Merge {
	
	public static final List<SVNConflictDescription> NO_CONFLICTS = Collections.emptyList();
	
	public long revision;
	public Collection<SVNModule> changedModules;

	private final boolean revert;

	public Merge(long revision, Collection<SVNModule> changedModules, boolean revert) {
		this.revision = revision;
		this.changedModules = changedModules;
		this.revert = revert;
	}

	public List<SVNConflictDescription> run(MergeContext context) throws SVNException {
		File workspaceRoot = context.config.getWorkspaceRoot();
		
		List<SVNConflictDescription> allConflicts = NO_CONFLICTS;
		
		for (SVNModule module : this.changedModules) {
			SVNURL startURL = module.getURL();
			File dstPath = new File(workspaceRoot, module.getName());

			List<SVNConflictDescription> moduleConflicts = doMerge(context.diffClient, startURL, dstPath);
			if (moduleConflicts != NO_CONFLICTS) {
				if (allConflicts == NO_CONFLICTS) {
					allConflicts = moduleConflicts;
				} else {
					allConflicts.addAll(moduleConflicts);
				}
			}
		}
		return allConflicts;
	}
	
	List<SVNConflictDescription> doMerge(SVNDiffClient diffClient, SVNURL url, File dstPath) throws SVNException {
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
		merge.addTarget(target);

		SvnTarget source = SvnTarget.fromURL(url, startRevision);
		merge.setSource(source, false);
		SVNRevisionRange range = new SVNRevisionRange(startRevision, endRevision);
		merge.addRevisionRange(SvnCodec.revisionRange(range));
		
		merge.setIgnoreAncestry(revert);
		
		System.out.println("svn merge " + (revert ? "--ignore-ancestry ": "") + "-r" + range.getStartRevision() + ":" + range.getEndRevision() + " " + source + " " + target.getFile());
		
		ISVNOptions options = merge.getOptions();
		if (options instanceof DefaultSVNOptions) {
			final ISVNConflictHandler conflictResolver = options.getConflictResolver();
			final ArrayList<SVNConflictDescription> mergeConflicts = new ArrayList<SVNConflictDescription>();
			try {
				((DefaultSVNOptions) options).setConflictHandler(new ISVNConflictHandler() {

					@Override
					public SVNConflictResult handleConflict(SVNConflictDescription conflictDescription)
							throws SVNException {
						SVNConflictResult result;
						if (conflictResolver == null) {
							result = new SVNConflictResult(SVNConflictChoice.POSTPONE, null);
						} else {
							result = conflictResolver.handleConflict(conflictDescription);
						}
						mergeConflicts.add(conflictDescription);
						return result;
					}
				});
				merge.run();
				return checkConflicts(mergeConflicts);
			} finally {
				((DefaultSVNOptions) options).setConflictHandler(conflictResolver);
			}
		} else {
			Log.info("No chance to resolve conflicts");
			merge.run();
			return NO_CONFLICTS;
		}
	}

	private List<SVNConflictDescription> checkConflicts(List<SVNConflictDescription> mergeConflicts) {
		if (mergeConflicts.isEmpty()) {
			return NO_CONFLICTS;
		} else {
			return mergeConflicts;
		}
	}


}
