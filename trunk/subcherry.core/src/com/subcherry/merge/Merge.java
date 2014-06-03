package com.subcherry.merge;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc2.ISvnOperationHandler;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
 * @version    $Revision$  $Author$  $Date$
 */
public class Merge {
	
	public static final Map<File, List<SVNConflictDescription>> NO_CONFLICTS = Collections.emptyMap();
	
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
		
		Map<File, List<SVNConflictDescription>> allConflicts = NO_CONFLICTS;
		
		for (MergeResource resource : this.resources) {
			SVNURL startURL = resource.getURL();
			File dstPath = new File(workspaceRoot, resource.getName());

			Map<File, List<SVNConflictDescription>> moduleConflicts =
				doMerge(context.diffClient, startURL, dstPath, resource.getIgnoreAncestry());
			if (moduleConflicts != NO_CONFLICTS) {
				if (allConflicts == NO_CONFLICTS) {
					allConflicts = moduleConflicts;
				} else {
					allConflicts.putAll(moduleConflicts);
				}
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
		
		Set<File> touchedFiles = new HashSet<File>();
		ISVNEventHandler formerHandler = installFileCollectHandler(merge, touchedFiles);
		try {
			Map<File, List<SVNConflictDescription>> mergeConflicts= new HashMap<File, List<SVNConflictDescription>>();
			ISvnOperationHandler formerOperationHandler = installOperationHandler(merge, mergeConflicts, touchedFiles);
			try {
				merge.run();
				return checkConflicts(mergeConflicts);
			} finally {
				restoreOperationHandler(merge, formerOperationHandler);
			}
		} finally {
			restoreFormerHandler(merge, formerHandler);
		}
	}

	private static void restoreOperationHandler(SvnMerge merge, ISvnOperationHandler formerHandler) {
		SvnOperationFactory operationFactory = merge.getOperationFactory();
		operationFactory.setOperationHandler(formerHandler);
	}

	private static ISvnOperationHandler installOperationHandler(SvnMerge merge,
			final Map<File, List<SVNConflictDescription>> mergeConflicts, final Iterable<File> touchedFiles) {
		SvnOperationFactory operationFactory = merge.getOperationFactory();
		final ISvnOperationHandler formerHandler = operationFactory.getOperationHandler();
		operationFactory.setOperationHandler(new ISvnOperationHandler() {
			
			@Override
			public void beforeOperation(SvnOperation<?> operation) throws SVNException {
				if (formerHandler != null) {
					formerHandler.beforeOperation(operation);
				}
			}
			
			@Override
			public void afterOperationSuccess(SvnOperation<?> operation) throws SVNException {
				if (formerHandler != null) {
					formerHandler.afterOperationSuccess(operation);
				}
				ISVNWCDb wcDb = operation.getOperationFactory().getWcContext().getDb();
				for (File f : touchedFiles) {
					List<SVNConflictDescription> conflicts = wcDb.readConflicts(f);
					if (!conflicts.isEmpty()) {
						mergeConflicts.put(f, conflicts);
					}
				}
			}
			
			@Override
			public void afterOperationFailure(SvnOperation<?> operation) {
				if (formerHandler != null) {
					formerHandler.afterOperationFailure(operation);
				}
			}

		});
		return formerHandler;
	}

	private static void restoreFormerHandler(SvnMerge merge, ISVNEventHandler formerHandler) {
		SvnOperationFactory operationFactory = merge.getOperationFactory();
		operationFactory.setEventHandler(formerHandler);
	}

	private static ISVNEventHandler installFileCollectHandler(SvnMerge merge, final Set<File> touchedFiles) {
		SvnOperationFactory operationFactory = merge.getOperationFactory();
		final ISVNEventHandler formerEventHandler = operationFactory.getEventHandler();
		operationFactory.setEventHandler(new ISVNEventHandler() {
			
			@Override
			public void checkCancelled() throws SVNCancelException {
				if (formerEventHandler != null) {
					formerEventHandler.checkCancelled();
				}
				// not canceled by any button or so.
			}
			
			@Override
			public void handleEvent(SVNEvent event, double progress)
					throws SVNException {
				if (formerEventHandler != null) {
					formerEventHandler.handleEvent(event, progress);
				}
				touchedFiles.add(event.getFile());
			}
		});
		return formerEventHandler;
	}

	private Map<File, List<SVNConflictDescription>> checkConflicts(Map<File, List<SVNConflictDescription>> mergeConflicts) {
		if (mergeConflicts.isEmpty()) {
			return NO_CONFLICTS;
		} else {
			return mergeConflicts;
		}
	}


}
