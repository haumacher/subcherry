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
import java.util.regex.Pattern;

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
		_operations = new ArrayList<>();
		_virtualFs.clear();
		_crossMergedDirectories = new HashSet<>();
		_touchedResources = new HashSet<>();

		// It is not probable that multiple change sets require the same copy revision.
		_additionalRevisions.clear();

		buildOperations(logEntry);
		return new Merge(logEntry.getRevision(), _operations, _touchedResources);
	}

	private void buildOperations(SVNLogEntry logEntry) throws SVNException {
		AdditionalRevision additionalInfo = _config.getAdditionalRevisions().get(logEntry.getRevision());
		Set<String> includePaths;
		if (additionalInfo != null) {
			includePaths = additionalInfo.getIncludePaths();
		} else {
			includePaths = null;
		}
		
		if (includePaths == null) {
			boolean hasMoves = _config.getSemanticMoves() && handleCopies(logEntry);
			if (hasMoves) {
				addRecordOnly(logEntry, new CompleteModuleChangeSetBuilder());
			} else {
				addMerges(logEntry, new CompleteModuleChangeSetBuilder());
			}
		} else {
			addMerges(logEntry, new PartialChangeSetBuilder(includePaths));
		}
	}

	private boolean hasNoCopies(SVNLogEntry logEntry) {
		for (SVNLogEntryPath pathEntry : logEntry.getChangedPaths().values()) {
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
	private boolean handleCopies(SVNLogEntry logEntry) throws SVNException {
		if (hasNoCopies(logEntry)) {
			// Optimization.
			return false;
		}

		boolean hasMoves = false;

		List<SVNLogEntryPath> entries = pathOrder(logEntry.getChangedPaths().values());
		for (int index = 0, cnt = entries.size(); index < cnt; index++) {
			SVNLogEntryPath svnPathEntry = entries.get(index);

			String pathName = svnPathEntry.getPath();
			if (!usePath(pathName)) {
				continue;
			}

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
				if (target.isDir() && (target.getType() == ChangeType.ADDED || target.getType() == ChangeType.REPLACED)) {
					// Test, whether children nodes have copy-from information. In that case, the
					// created directory cannot be cross-branch copied, to allow handling copies
					// (from outside) in the created directory.
					boolean containsCopiedPaths = false;
					for (int childIndex = index + 1; childIndex < cnt; childIndex++) {
						SVNLogEntryPath childEntry = entries.get(childIndex);
						String childPathName = childEntry.getPath();
						if (childPathName.length() < pathName.length() + 1) {
							break;
						}
						if (!childPathName.startsWith(pathName)) {
							break;
						}
						if (childPathName.charAt(pathName.length()) != '/') {
							break;
						}

						if (childEntry.getCopyPath() != null) {
							containsCopiedPaths = true;
							break;
						}
					}

					if (containsCopiedPaths) {
						if (target.getType() == ChangeType.REPLACED) {
							addRemove(target.getResource());
						}
						SvnOperation<?> mkDir = mkDir(target.getResource());
						addOperation(target.getResource(), mkDir);
						continue;
					}
				}

				// Plain add or modify, no source specified. Merge directly.
				directMerge(logEntry.getRevision(), target);
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

				boolean intraBranchCopy = srcResourceChange.getBranch().equals(srcPath.getBranch());
				{
					File srcFile;
					boolean srcExistsBefore;
					if (intraBranchCopy && _modules.contains(srcModule)) {
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
					List<SVNLogEntry> intermediateChanges = Collections.emptyList();
					if (srcExistsBefore) {
						if (copiedRevision < srcRevision - 1) {
							// The copy potentially is a revert.
							intermediateChanges = getChanges(srcPath.getPath(), copiedRevision, srcRevision);
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

					boolean noOpCopy = srcExistsBefore && srcResource.equals(target.getResource());
					if (!noOpCopy) {
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

						SvnCopy copy = operations().createCopy();
						copy.setDepth(SVNDepth.INFINITY);
						copy.setMakeParents(true);
						// Note: Must not ignore existance: If a directory is copied, and the
						// destination path exists, the directory is copied into the existing
						// directory, instead of its content.
						copy.setFailWhenDstExists(true);
						// It is hard to determine, whether the source must be delete at the time
						// the destination of the move is copied. Just handle the deletion at the
						// time, the original deletion occurred (if it is de-facto a move).
						copy.setMove(false);
						copy.addCopySource(copySource);
						copy.setSingleTarget(svnTarget);
						addOperation(target.getResource(), copy);
						_virtualFs.add(target.getResource());
					}

					if (intermediateChanges.size() > 0) {
						// There was a commit on the copied resource between the merged revision
						// and the revision from which was copied from. De-facto, this commit
						// reverts the copied resource to a version not currently alive. Such
						// revert cannot be easily done within the current working copy, because
						// it is unclear what is the corrensponding revision, to which the
						// copied file must be reverted.

						SVNRevision startRevision = SVNRevision.create(srcRevision - 1);
						SVNRevision endRevision = SVNRevision.create(copiedRevision);
						SVNRevision pegRevision = startRevision;

						String mergeSourcePath = srcResourceChange.getCopyPath().getPath();
						SvnTarget mergeSource =
							SvnTarget.fromURL(svnUrl(_config.getSvnURL() + mergeSourcePath), pegRevision);

						SvnMerge merge = operations().createMerge();
						merge.setAllowMixedRevisions(true);
						merge.setIgnoreAncestry(true);
						merge.addRevisionRange(SvnRevisionRange.create(startRevision, endRevision));
						merge.setSource(mergeSource, false);
						merge.setSingleTarget(svnTarget);
						addOperation(target.getResource(), merge);

						for (SVNLogEntry intermediateChange : intermediateChanges) {
							for (SVNLogEntryPath changedPathEntry : intermediateChange.getChangedPaths().values()) {
								String originalRevertPath = changedPathEntry.getPath();
								if (!isSubPath(originalRevertPath, mergeSourcePath)) {
									continue;
								}

								String rewrittenRevertPath = target.getPath() + originalRevertPath.substring(mergeSourcePath.length());
								addCommitResource(_paths.parsePath(rewrittenRevertPath).getResource());
							}
						}
					}

					// Apply potential content changes throughout the copy chain (starting with the
					// first original intra-branch copy).
					for (int n = sources.size() - 1; n >= 0; n--) {
						ResourceChange mergedChange = sources.get(n);
						SvnMerge merge = mergeContentChanges(svnTarget, mergedChange);
						if (n == 0) {
							merge.setDepth(SVNDepth.EMPTY);
						}
						addOperation(target.getResource(), merge);
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

	private static boolean isSubPath(String subPath, String parentPath) {
		int parentLength = parentPath.length();
		return subPath.length() > parentLength && subPath.startsWith(parentPath) && subPath.charAt(parentLength) == '/';
	}

	private SvnOperation<?> mkDir(String resource) {
		SvnOperation<Void> localMkDir = new LocalMkDir(operations());
		localMkDir.setSingleTarget(SvnTarget.fromFile(new File(_config.getWorkspaceRoot(), resource)));
		return localMkDir;
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

	private SvnMerge mergeContentChanges(SvnTarget target, ResourceChange mergedChange)
			throws SVNException {
		SVNLogEntry mergedChangeSet = mergedChange.getChangeSet();
		long mergedRevision = mergedChangeSet.getRevision();
		Path mergedResourceChange = mergedChange.getChange();
		String origTargetPath = mergedResourceChange.getPath();

		if (mergedResourceChange.getPathEntry().getKind() == SVNNodeKind.DIR) {
			String dirPrefix = origTargetPath + '/';
			for (SVNLogEntryPath contentChange : mergedChangeSet.getChangedPaths().values()) {
				String contentPathName = contentChange.getPath();
				if (contentPathName.startsWith(dirPrefix)) {
					Path contentPath = _paths.parsePath(contentChange);
					addCommitResource(contentPath.getResource());
				}
			}
		}

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
		return merge;
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
	 * @return The changes to the given path between the two given revisions (exclusive).
	 */
	private List<SVNLogEntry> getChanges(String path, long copiedRevision, long mergedRevision) throws SVNException {
		class Counter implements ISVNLogEntryHandler {
			private final List<SVNLogEntry> _changes = new ArrayList<>();

			@Override
			public void handleLogEntry(SVNLogEntry intermediateChange) throws SVNException {
				_changes.add(intermediateChange);
			}

			public List<SVNLogEntry> getChanges() {
				return _changes;
			}
		}
		Counter counter = new Counter();
		SVNRevision beforeMergedSvnRevision = SVNRevision.create(mergedRevision - 1);
		SVNRevision afterCopiedRevision = SVNRevision.create(copiedRevision + 1);
		SVNRevision copiedSvnRevision = SVNRevision.create(copiedRevision);
		_clientManager.getLogClient().doLog(svnUrl(_config.getSvnURL()),
			new String[] { path }, copiedSvnRevision, beforeMergedSvnRevision,
			afterCopiedRevision, true, true, false, 0, NO_PROPERTIES,
			counter);
		return counter.getChanges();
	}

	private void directMerge(long revision, Path target) throws SVNException {
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
			_explicitPathChangeSetBuilder.buildMerge(revision, target, false, true);
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

	private void addRecordOnly(SVNLogEntry logEntry, MergeBuilder builder) throws SVNException {
		createMerges(logEntry, builder, true);
	}

	private void addMerges(SVNLogEntry logEntry, MergeBuilder builder) throws SVNException {
		createMerges(logEntry, builder, false);
	}

	private void createMerges(SVNLogEntry logEntry, MergeBuilder builder, boolean recordOnly) throws SVNException {
		Map<String, SVNLogEntryPath> changedPaths = logEntry.getChangedPaths();
		for (Entry<String, SVNLogEntryPath> entry : changedPaths.entrySet()) {
			SVNLogEntryPath pathEntry = entry.getValue();
			if (!usePath(pathEntry.getPath())) {
				continue;
			}

			Path changedPath = _paths.parsePath(pathEntry);
			if (changedPath.getBranch() == null) {
				Log.warning("Path does not match the branch pattern: " + changedPath);
				continue;
			}

			builder.buildMerge(logEntry.getRevision(), changedPath, recordOnly, false);
		}
	}

	private boolean usePath(String path) {
		Pattern excludePaths = _config.getExcludePaths();
		if (excludePaths != null) {
			if (excludePaths.matcher(path).matches()) {
				return false;
			}
		}
		Pattern includePaths = _config.getIncludePaths();
		if (includePaths != null) {
			if (!includePaths.matcher(path).matches()) {
				return false;
			}
		}
		return true;
	}

	String createUrlPrefix(String branch) {
		return _config.getSvnURL() + branch;
	}

	void addMergeOperations(long revision, Path path, String resourceName, String urlPrefix,
			boolean recordOnly, boolean ignoreAncestry) throws SVNException {
		switch (path.getType()) {
			case DELETED: {
				if (!recordOnly) {
					addRemove(resourceName);
				}
				break;
			}
			case ADDED: {
				if (!recordOnly) {
					addRemoteAdd(revision, path, resourceName);
				}
				addModification(revision, resourceName, urlPrefix, recordOnly, ignoreAncestry);
				break;
			}
			case REPLACED: {
				if (!recordOnly) {
					addRemove(resourceName);
					addRemoteAdd(revision, path, resourceName);
				}
				addModification(revision, resourceName, urlPrefix, recordOnly, ignoreAncestry);
				break;
			}
			case MODIFIED: {
				addModification(revision, resourceName, urlPrefix, recordOnly, ignoreAncestry);
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

	private void addRemoteAdd(long revision, Path path, String targetResource) throws SVNException {
		addOperation(targetResource, createRemoteAdd(revision, path, targetResource));
		_virtualFs.add(targetResource);
	}

	private SvnOperation<?> createRemoteAdd(long srcRevision, Path path, String targetResource) throws SVNException {
		SVNRevision revision;
		SvnCopySource copySource;
		if (path.getCopyPath() == null) {
			revision = SVNRevision.create(srcRevision);
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

	void addModification(long revision, String targetResource, String urlPrefix, boolean recordOnly, boolean ignoreAncestry)
			throws SVNException {
		addOperation(targetResource, createModification(revision, targetResource, urlPrefix, recordOnly, ignoreAncestry));
	}

	SvnMerge createModification(long revision, String resourceName, String urlPrefix, boolean recordOnly, boolean ignoreAncestry)
			throws SVNException {
		SvnMerge merge = operations().createMerge();
		merge.setRecordOnly(recordOnly);
		boolean revert = _config.getRevert();
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

		public abstract void buildMerge(long revision, Path path, boolean recordOnly, boolean ignoreAncestry) throws SVNException;

	}

	class CompleteModuleChangeSetBuilder extends MergeBuilder {

		Set<String> _mergedModules = new HashSet<>();

		public CompleteModuleChangeSetBuilder() {
			super();
		}

		@Override
		public void buildMerge(long revision, Path path, boolean recordOnly, boolean ignoreAncestry) throws SVNException {
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

			addModification(revision, path.getModule(), createUrlPrefix(path.getBranch()), recordOnly, ignoreAncestry);
		}
	}

	class PartialChangeSetBuilder extends MergeBuilder {

		private Set<String> _includePaths;

		public PartialChangeSetBuilder(Set<String> includePaths) {
			_includePaths = includePaths;
		}

		@Override
		public void buildMerge(long revision, Path path, boolean recordOnly, boolean ignoreAncestry) throws SVNException {
			if (!_includePaths.contains(path.getResource())) {
				// Skip path.
				return;
			}

			String urlPrefix = createUrlPrefix(path.getBranch());
			addMergeOperations(revision, path, path.getResource(), urlPrefix, recordOnly, true);
		}
	}

	class ExplicitPathChangeSetBuilder extends MergeBuilder {
		@Override
		public void buildMerge(long revision, Path path, boolean recordOnly, boolean ignoreAncestry) throws SVNException {
			String urlPrefix = createUrlPrefix(path.getBranch());
			if (path.getPathEntry().getKind() == SVNNodeKind.DIR
				&& path.getPathEntry().getType() == SVNLogEntryPath.TYPE_MODIFIED) {
				SvnMerge merge =
					createModification(revision, path.getResource(), urlPrefix, recordOnly, ignoreAncestry);
				merge.setDepth(SVNDepth.EMPTY);
				addOperation(path.getResource(), merge);
			} else {
				addMergeOperations(revision, path, path.getResource(), urlPrefix, recordOnly, ignoreAncestry);
			}
		}
	}

	private static SVNURL svnUrl(String svnURL) throws SVNException {
		return SVNURL.parseURIDecoded(svnURL);
	}

}
