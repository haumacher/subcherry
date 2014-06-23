package com.subcherry.merge;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnScheduleForRemoval;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import com.subcherry.AdditionalRevision;
import com.subcherry.MergeConfig;
import com.subcherry.history.ChangeType;
import com.subcherry.util.VirtualFS;
import com.subcherry.utils.Log;
import com.subcherry.utils.Path;
import com.subcherry.utils.PathParser;


/**
 * @version $Revision$ $Author$ $Date$
 */
public class MergeHandler extends Handler<MergeConfig> {

	private static final String[] ROOT = { "/" };

	private static final String[] NO_PROPERTIES = {};

	final Set<String> _modules;

	private SVNLogEntry _logEntry;

	private List<SvnOperation<?>> _operations;

	private SVNClientManager _clientManager;

	private Map<SVNRevision, SVNLogEntry> _additionalRevisions = new HashMap<>();

	private final VirtualFS _virtualFs;

	private Set<String> _crossMergedDirectories;

	private Set<String> _touchedResources;

	private final ExplicitPathChangeSetBuilder _explicitPathChangeSetBuilder = new ExplicitPathChangeSetBuilder();

	private final PathParser _paths;

	public MergeHandler(SVNClientManager clientManager, MergeConfig config, PathParser paths, Set<String> modules) {
		super(config);
		_clientManager = clientManager;
		_paths = paths;
		_modules = modules;
		_virtualFs = new VirtualFS();
	}

	private SvnOperationFactory operations() {
		return diffClient().getOperationsFactory();
	}

	private SVNDiffOptions mergeOptions() {
		return diffClient().getMergeOptions();
	}

	private SVNDiffClient diffClient() {
		return _clientManager.getDiffClient();
	}

	public Merge parseMerge(SVNLogEntry logEntry) throws SVNException {
		_logEntry = logEntry;
		_operations = new ArrayList<>();
		_virtualFs.clear();
		_crossMergedDirectories = new HashSet<>();
		_touchedResources = new HashSet<>();

		// It is not probable that multiple change sets require the same copy revision.
		_additionalRevisions.clear();

		buildOperations();
		return new Merge(_logEntry.getRevision(), _operations, _touchedResources);
	}

	private void buildOperations() throws SVNException {
		AdditionalRevision additionalInfo = _config.getAdditionalRevisions().get(_logEntry.getRevision());
		Set<String> includePaths;
		if (additionalInfo != null) {
			includePaths = additionalInfo.getIncludePaths();
		} else {
			includePaths = null;
		}
		
		if (includePaths == null) {
			boolean hasMoves = _config.getSemanticMoves() && handleCopies();
			if (hasMoves) {
				addRecordOnly(new CompleteModuleChangeSetBuilder());
			} else {
				addMerges(new CompleteModuleChangeSetBuilder());
			}
		} else {
			addMerges(new PartialChangeSetBuilder(includePaths));
		}
	}

	private boolean hasNoCopies() {
		for (SVNLogEntryPath pathEntry : _logEntry.getChangedPaths().values()) {
			if (pathEntry.getCopyPath() != null) {
				// There is potentially a change that must be treated especially.
				return false;
			}
		}
		return true;
	}

	/**
	 * Creates merge operations that represent semantic moves and copies.
	 * 
	 * @return Whether the current changeset has semantic moves or copies.
	 */
	private boolean handleCopies() throws SVNException {
		if (hasNoCopies()) {
			// Optimization.
			return false;
		}

		boolean hasMoves = false;

		SVNLogEntry logEntry = _logEntry;
		for (SVNLogEntryPath svnPathEntry : pathOrder(logEntry.getChangedPaths().values())) {
			final Path target = _paths.parsePath(svnPathEntry);

			if (!_modules.contains(target.getModule())) {
				// The change happened in a module that is not among the merged modules, drop the
				// change.
				continue;
			}

			if (anchestorCrossMerged(target.getResource())) {
				// Some ancestor of the current path has been cross-branch copied directly from the
				// merged change set. All descendants of this ancestor are implicitly copied as well
				// and must not be merged again.
				continue;
			}

			if (target.getCopyPath() == null) {
				// Plain add or modify, no source specified. Merge directly.
				directMerge(target);
				continue;
			}

			{
				List<ResourceChange> sources =
					getMergeSources(new ResourceChange(logEntry, target), target.getBranch());

				ResourceChange srcChange = sources.get(sources.size() - 1);
				long srcRevision = srcChange.getChangeSet().getRevision();
				Path srcResourceChange = srcChange.getChange();

				Path srcPath = srcResourceChange.getCopyPath();
				String srcModule = srcPath.getModule();
				String srcResource = srcPath.getResource();

				boolean isMove = isDeleted(logEntry, target.getBranch() + srcResource);
				
				{
					File srcFile;
					boolean srcExistsBefore;
					if (_modules.contains(srcModule)) {
						srcFile = new File(_config.getWorkspaceRoot(), srcResource);
						srcExistsBefore = srcFile.exists();
					} else {
						// Copied from a module that is not part of the current merge. Perform a
						// regular cross-branch copy of the content.
						srcFile = null;
						srcExistsBefore = false;
					}

					File targetFile = new File(_config.getWorkspaceRoot(), target.getResource());

					long copiedRevision = srcResourceChange.getCopyRevision();
					if (srcExistsBefore) {
						if (copiedRevision < srcRevision - 1) {
							// The copy potentially is a revert.
							int changeCount = getChangeCount(srcPath.getPath(), copiedRevision, srcRevision);
							if (changeCount > 0) {
								// There was a commit on the copied resource between the merged revision
								// and the revision from which was copied from. De-facto, this commit
								// reverts the copied resource to a version not currently alive. Such
								// revert cannot be easily done within the current working copy, because
								// it is unclear what is the corrensponding revision, to which the
								// copied file should be reverted.
								
								// TODO: The revert should be transformed to a file-system copy of the
								// contents of the previous version (the copy source) into the target
								// resouce plus applying the changes in the merged revision.

								// Create a cross-branch copy.
								srcExistsBefore = false;
							}
						}
					}

					hasMoves = true;
					SvnTarget svnTarget = SvnTarget.fromFile(targetFile);

					boolean removeBeforeMerge;
					if (target.getType() == ChangeType.REPLACED) {
						// Delete target before re-creating. Otherwise copy will fail.
						removeBeforeMerge = existsWhenMerged(target.getResource());
					} else {
						removeBeforeMerge = false;
					}

					{
						boolean srcExistsWhenMerged;
						if (srcExistsBefore) {
							srcExistsWhenMerged = existsWhenMerged(srcResource);
						} else {
							// Not even present at the beginning of the merge.
							srcExistsWhenMerged = false;
						}

						if (isMove) {
							_virtualFs.delete(srcResource);
							addCommitResource(srcResource);
						}

						if (removeBeforeMerge) {
							addRemove(target.getResource());
						}

						SvnCopySource copySource;
						if (!srcExistsBefore) {
							// Keep a remote cross branch copy.
							SVNURL srcUrl = svnUrl(_config.getSvnURL() + srcResourceChange.getCopyPath());
							SVNRevision copiedSvnRevision = SVNRevision.create(copiedRevision);
							
							copySource =
								SvnCopySource.create(SvnTarget.fromURL(srcUrl, copiedSvnRevision), copiedSvnRevision);
						} else {
							copySource =
								SvnCopySource.create(SvnTarget.fromFile(srcFile, SVNRevision.BASE), SVNRevision.BASE);
						}

						boolean moveInWorkingCopy;
						if (srcExistsWhenMerged) {
							moveInWorkingCopy = isMove;
						} else {
							// At the time, the copy will be executed, the source of the move has
							// already been deleted due to other actions. Therefore, a plain copy
							// must be executed, because a move of a file in revision BASE will
							// fail.
							moveInWorkingCopy = false;
						}

						SvnCopy copy = operations().createCopy();
						copy.setDepth(SVNDepth.INFINITY);
						copy.setMakeParents(true);
						// Note: Must not ignore existance: If a directory is copied, and the
						// destination path exists, the directory is copied into the existing
						// directory, instead of its content.
						copy.setFailWhenDstExists(true);
						copy.setMove(moveInWorkingCopy);
						copy.addCopySource(copySource);
						copy.setSingleTarget(svnTarget);
						addOperation(target.getResource(), copy);
						_virtualFs.add(target.getResource());
					}

					// Apply potential content changes throughout the copy chain (starting with the
					// first original intra-branch copy).
					for (int n = sources.size() - 1; n >= 0; n--) {
						ResourceChange mergedChange = sources.get(n);
						mergeContentChanges(target.getResource(), svnTarget, mergedChange);
					}
				}
			}
		}

		if (!hasMoves) {
			// Revert singleton merges.
			_operations.clear();
			_touchedResources.clear();
			_virtualFs.clear();
			_crossMergedDirectories.clear();
		}
		return hasMoves;
	}

	private boolean anchestorCrossMerged(String resource) {
		if (_crossMergedDirectories.isEmpty()) {
			// Optimization.
			return false;
		}

		while (true) {
			if (_crossMergedDirectories.contains(resource)) {
				return true;
			}

			int dirSeparatorIndex = resource.lastIndexOf('/');
			if (dirSeparatorIndex < 0) {
				return false;
			}

			resource = resource.substring(0, dirSeparatorIndex);
		}
	}

	private boolean existsWhenMerged(final String resource) {
		return _virtualFs.exists(resource);
	}

	private void mergeContentChanges(String targetResource, SvnTarget target, ResourceChange mergedChange)
			throws SVNException {
		long mergedRevision = mergedChange.getChangeSet().getRevision();
		Path mergedResourceChange = mergedChange.getChange();
		String origTargetPath = mergedResourceChange.getPath();

		SVNRevision revisionBefore = SVNRevision.create(mergedRevision - 1);
		SVNRevision changeRevision = SVNRevision.create(mergedRevision);

		SVNURL origTargetUrl = svnUrl(_config.getSvnURL() + origTargetPath);
		SvnTarget mergeSource = SvnTarget.fromURL(origTargetUrl, changeRevision);

		SvnMerge merge = operations().createMerge();
		merge.setAllowMixedRevisions(true);
		merge.setIgnoreAncestry(true);
		merge.addRevisionRange(SvnRevisionRange.create(revisionBefore, changeRevision));
		merge.setSource(mergeSource, false);
		merge.setSingleTarget(target);
		addOperation(targetResource, merge);
	}

	/**
	 * The number of changes on the given path between the copy source revision and the revision
	 * performing the copy.
	 * 
	 * @param path
	 *        The tested path.
	 * @param copiedRevision
	 *        The source revision of the copy.
	 * @param mergedRevision
	 *        The revision committing the copy.
	 * @return The number of changes to the given path between the two given revisions (exclusive).
	 */
	private int getChangeCount(String path, long copiedRevision, long mergedRevision) throws SVNException {
		class Counter implements ISVNLogEntryHandler {
			private int _cnt;

			@Override
			public void handleLogEntry(SVNLogEntry intermediateChange) throws SVNException {
				_cnt++;
			}

			public int getCnt() {
				return _cnt;
			}
		}
		Counter counter = new Counter();
		SVNRevision beforeMergedSvnRevision = SVNRevision.create(mergedRevision - 1);
		SVNRevision afterCopiedRevision = SVNRevision.create(copiedRevision + 1);
		SVNRevision copiedSvnRevision = SVNRevision.create(copiedRevision);
		_clientManager.getLogClient().doLog(svnUrl(_config.getSvnURL()),
			new String[] { path }, copiedSvnRevision, beforeMergedSvnRevision,
			afterCopiedRevision, true, false, false, 0, NO_PROPERTIES,
			counter);
		return counter.getCnt();
	}

	private void directMerge(Path target) throws SVNException {
		String resource = target.getResource();
		ChangeType changeType = target.getType();

		if (changeType == ChangeType.ADDED || changeType == ChangeType.REPLACED) {
			if (target.getPathEntry().getKind() == SVNNodeKind.DIR) {
				_crossMergedDirectories.add(resource);
			}
		}

		// Prevent merging the whole module (if, e.g. merge info is merged for the module),
		// since this would produce conflicts with the explicitly merged moves and copies.
		if (!_modules.contains(resource) && existsWhenMerged(resource)) {
			_explicitPathChangeSetBuilder.buildMerge(target, false, true);
		}
	}

	/**
	 * Compute the source change to merge for a given original change.
	 * 
	 * @param origChange
	 *        The original change to merge (read form the log of the merge).
	 * @param targetBranch
	 *        The branch to apply the change to.
	 * @return The changes (copies with potential content modifications) to apply to the target
	 *         branch. The original (intra-branch) copy is the last change in the list.
	 *         <code>null</code> if the copy chain does not end in an intra-branch copy.
	 */
	private List<ResourceChange> getMergeSources(ResourceChange origChange, final String targetBranch)
			throws SVNException {
		ArrayList<ResourceChange> result = new ArrayList<>();
		result.add(origChange);

		ResourceChange mergedChange = origChange;
		Path copyPath = mergedChange.getChange().getCopyPath();
		String copyBranch = copyPath.getBranch();
		if (!copyBranch.equals(targetBranch)) {
			while (true) {
				SVNLogEntry origEntry = loadRevision(mergedChange.getChange().getCopyRevision());
				SVNLogEntryPath origPathEntry = origEntry.getChangedPaths().get(copyPath.getPath());
				if (origPathEntry == null) {
					// Not copied directly from a copy/move changeset.
					break;
				}

				Path origPath = _paths.parsePath(origPathEntry);
				Path origCopyPath = origPath.getCopyPath();
				if (origCopyPath == null) {
					// Cannot be followed to an intra-branch copy (was a plain add in the
					// original change).
					break;
				}

				mergedChange = new ResourceChange(origEntry, origPath);
				result.add(mergedChange);

				String origCopyBranch = origCopyPath.getBranch();
				if (origCopyBranch.equals(copyBranch)) {
					// Found an intra-branch copy, replay this copy.
					break;
				}

				copyPath = origCopyPath;
				copyBranch = origCopyBranch;
			}
		}

		return result;
	}

	private List<SVNLogEntryPath> pathOrder(Collection<SVNLogEntryPath> values) {
		ArrayList<SVNLogEntryPath> result = new ArrayList<>(values);
		Collections.sort(result, new Comparator<SVNLogEntryPath>() {
			@Override
			public int compare(SVNLogEntryPath p1, SVNLogEntryPath p2) {
				return p1.getPath().compareTo(p2.getPath());
			}
		});
		return result;
	}

	private SVNLogEntry loadRevision(long revision) throws SVNException {
		SVNRevision svnRevision = SVNRevision.create(revision);

		SVNLogEntry result = _additionalRevisions.get(svnRevision);
		if (result == null) {
			LastLogEntry handler = new LastLogEntry();

			// Retrieve the original log entry.
			boolean stopOnCopy = false;
			boolean discoverChangedPaths = true;
			boolean includeMergedRevisions = false;
			_clientManager.getLogClient().doLog(svnUrl(_config.getSvnURL()), ROOT,
				svnRevision, svnRevision, svnRevision,
				stopOnCopy, discoverChangedPaths, includeMergedRevisions, 0, NO_PROPERTIES, handler);

			result = handler.getLogEntry();
			_additionalRevisions.put(svnRevision, result);
		}
		return result;
	}

	private void addRecordOnly(MergeBuilder builder) throws SVNException {
		createMerges(builder, true);
	}

	private void addMerges(MergeBuilder builder) throws SVNException {
		createMerges(builder, false);
	}

	private static boolean isDeleted(SVNLogEntry logEntry, String path) {
		SVNLogEntryPath pathEntry = logEntry.getChangedPaths().get(path);
		if (pathEntry == null) {
			return false;
		}

		char type = pathEntry.getType();
		return type == SVNLogEntryPath.TYPE_DELETED || type == SVNLogEntryPath.TYPE_REPLACED;
	}

	private void createMerges(MergeBuilder builder, boolean recordOnly) throws SVNException {
		Map<String, SVNLogEntryPath> changedPaths = _logEntry.getChangedPaths();
		for (Entry<String, SVNLogEntryPath> entry : changedPaths.entrySet()) {
			SVNLogEntryPath pathEntry = entry.getValue();

			Path changedPath = _paths.parsePath(pathEntry);
			if (changedPath.getBranch() == null) {
				Log.warning("Path does not match the branch pattern: " + changedPath);
				continue;
			}

			builder.buildMerge(changedPath, recordOnly, false);
		}
	}

	String createUrlPrefix(String branch) {
		return _config.getSvnURL() + branch;
	}

	void addMergeOperations(Path path, String resourceName, String urlPrefix, boolean recordOnly,
			boolean ignoreAncestry) throws SVNException {
		switch (path.getType()) {
			case DELETED: {
				if (!recordOnly) {
					addRemove(resourceName);
				}
				break;
			}
			case ADDED: {
				if (!recordOnly) {
					addRemoteAdd(path, resourceName);
				}
				addModification(resourceName, urlPrefix, recordOnly, ignoreAncestry);
				break;
			}
			case REPLACED: {
				if (!recordOnly) {
					addRemove(resourceName);
					addRemoteAdd(path, resourceName);
				}
				addModification(resourceName, urlPrefix, recordOnly, ignoreAncestry);
				break;
			}
			case MODIFIED: {
				addModification(resourceName, urlPrefix, recordOnly, ignoreAncestry);
				break;
			}
		}
	}

	private void addOperation(String targetResource, SvnOperation<?> operation) {
		_operations.add(operation);
		addCommitResource(targetResource);
	}

	void addCommitResource(String resource) {
		_touchedResources.add(resource);
	}

	private void addRemoteAdd(Path path, String targetResource) throws SVNException {
		addOperation(targetResource, createRemoteAdd(path, targetResource));
		_virtualFs.add(targetResource);
	}

	private SvnOperation<?> createRemoteAdd(Path path, String targetResource) throws SVNException {
		SVNRevision revision;
		SvnCopySource copySource;
		if (path.getCopyPath() == null) {
			revision = SVNRevision.create(_logEntry.getRevision());
			copySource = SvnCopySource.create(
				SvnTarget.fromURL(svnUrl(_config.getSvnURL() + path.getPath()), revision),
				revision);
		} else {
			revision = SVNRevision.create(path.getCopyRevision());
			copySource = SvnCopySource.create(
				SvnTarget.fromURL(svnUrl(_config.getSvnURL() + path.getCopyPath()), revision),
				revision);
		}

		SvnCopy copy = operations().createCopy();
		copy.setRevision(revision);
		copy.setDepth(SVNDepth.INFINITY);
		copy.setMakeParents(true);
		copy.setFailWhenDstExists(false);
		copy.setMove(false);
		copy.addCopySource(copySource);
		copy.setSingleTarget(SvnTarget.fromFile(new File(_config.getWorkspaceRoot(), targetResource)));

		return copy;
	}

	void addModification(String targetResource, String urlPrefix, boolean recordOnly, boolean ignoreAncestry)
			throws SVNException {
		addOperation(targetResource, createModification(targetResource, urlPrefix, recordOnly, ignoreAncestry));
	}

	SvnMerge createModification(String resourceName, String urlPrefix, boolean recordOnly, boolean ignoreAncestry)
			throws SVNException {
		SvnMerge merge = operations().createMerge();
		merge.setRecordOnly(recordOnly);
		boolean revert = _config.getRevert();
		long revision = _logEntry.getRevision();
		SVNRevision startRevision = SVNRevision.create(revert ? revision : revision - 1);
		SVNRevision endRevision = SVNRevision.create(revert ? revision - 1 : revision);
		
		merge.setMergeOptions(mergeOptions());
		/* Must allow as otherwise the whole workspace is checked for revisions which costs much
		 * time */
		merge.setAllowMixedRevisions(true);
		File targetFile = new File(_config.getWorkspaceRoot(), resourceName);
		SvnTarget target = SvnTarget.fromFile(targetFile);
		merge.setSingleTarget(target);
		
		SVNURL sourceUrl = svnUrl(urlPrefix + resourceName);
		SvnTarget source = SvnTarget.fromURL(sourceUrl, endRevision);
		merge.setSource(source, false);
		SvnRevisionRange range = SvnRevisionRange.create(startRevision, endRevision);
		merge.addRevisionRange(range);
		
		merge.setIgnoreAncestry(revert || ignoreAncestry);
		return merge;
	}

	private void addRemove(String targetResource) {
		addOperation(targetResource, createRemove(targetResource));
		_virtualFs.delete(targetResource);
	}

	private SvnOperation<?> createRemove(String resourceName) {
		File targetFile = new File(_config.getWorkspaceRoot(), resourceName);

		SvnScheduleForRemoval remove = operations().createScheduleForRemoval();
		remove.setSingleTarget(SvnTarget.fromFile(targetFile));
		remove.setForce(true);
		remove.setDeleteFiles(true);
		remove.setDryRun(false);
		return remove;
	}

	abstract class MergeBuilder {

		public abstract void buildMerge(Path path, boolean recordOnly, boolean ignoreAncestry) throws SVNException;

	}

	class CompleteModuleChangeSetBuilder extends MergeBuilder {

		Set<String> _mergedModules = new HashSet<>();

		public CompleteModuleChangeSetBuilder() {
			super();
		}

		@Override
		public void buildMerge(Path path, boolean recordOnly, boolean ignoreAncestry) throws SVNException {
			String module = path.getModule();
			if (!_modules.contains(module)) {
				return;
			}

			if (!recordOnly) {
				addCommitResource(path.getResource());
			}

			if (_mergedModules.contains(module)) {
				return;
			}
			_mergedModules.add(module);

			addModification(path.getModule(), createUrlPrefix(path.getBranch()), recordOnly, ignoreAncestry);
		}
	}

	class PartialChangeSetBuilder extends MergeBuilder {

		private Set<String> _includePaths;

		public PartialChangeSetBuilder(Set<String> includePaths) {
			_includePaths = includePaths;
		}

		@Override
		public void buildMerge(Path path, boolean recordOnly, boolean ignoreAncestry) throws SVNException {
			if (!_includePaths.contains(path.getResource())) {
				// Skip path.
				return;
			}

			String urlPrefix = createUrlPrefix(path.getBranch());
			addMergeOperations(path, path.getResource(), urlPrefix, recordOnly, true);
		}
	}

	class ExplicitPathChangeSetBuilder extends MergeBuilder {
		@Override
		public void buildMerge(Path path, boolean recordOnly, boolean ignoreAncestry) throws SVNException {
			String urlPrefix = createUrlPrefix(path.getBranch());
			addMergeOperations(path, path.getResource(), urlPrefix, recordOnly, ignoreAncestry);
		}
	}

	private static SVNURL svnUrl(String svnURL) throws SVNException {
		return SVNURL.parseURIDecoded(svnURL);
	}

}
