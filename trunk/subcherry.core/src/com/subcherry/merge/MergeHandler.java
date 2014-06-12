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
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
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
import com.subcherry.Configuration;
import com.subcherry.history.ChangeType;
import com.subcherry.utils.Log;
import com.subcherry.utils.Utils;


/**
 * @version $Revision$ $Author$ $Date$
 */
public class MergeHandler extends Handler {

	private static final String[] ROOT = { "/" };

	private static final String[] NO_PROPERTIES = {};

	final Set<String> _modules;

	private SVNLogEntry _logEntry;

	private List<SvnOperation<?>> _operations;

	private SVNClientManager _clientManager;

	private SVNLogEntry _originalLogEntry;

	private boolean _originalEntryResolved;

	private Map<SVNRevision, SVNLogEntry> _additionalRevisions = new HashMap<>();

	private Set<String> _deletedPaths;

	private Set<String> _crossMergedPaths;

	private final MergeBuilder _explicitPathChangeSetBuilder = new ExplicitPathChangeSetBuilder();

	public MergeHandler(SVNClientManager clientManager, Configuration config, Set<String> modules) {
		super(config);
		_clientManager = clientManager;
		_modules = modules;
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
		_deletedPaths = new HashSet<>();
		_crossMergedPaths = new HashSet<>();

		// It is not probable that multiple change sets require the same copy revision.
		_additionalRevisions.clear();

		buildOperations();
		return new Merge(_logEntry.getRevision(), _operations);
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
			boolean hasMoves = _config.getSemanticMoves() && handleMoves();
			if (hasMoves) {
				addRecordOnly(new CompleteModuleChangeSetBuilder());
			} else {
				addMerges(new CompleteModuleChangeSetBuilder());
			}
		} else {
			addMerges(new PartialChangeSetBuilder(includePaths));
		}
	}

	private boolean hasNoMoves() {
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
	private boolean handleMoves() throws SVNException {
		if (hasNoMoves()) {
			// Optimization.
			return false;
		}

		boolean hasMoves = false;
		int operationCntBefore = _operations.size();

		SVNLogEntry logEntry = _logEntry;
		for (SVNLogEntryPath pathEntry : pathOrder(logEntry.getChangedPaths().values())) {
			final String targetPath = pathEntry.getPath();
			final String targetBranch = getBranch(targetPath);
			final String targetModule = getModuleName(targetPath, targetBranch.length());
			final String targetResource = getModulePath(targetPath, targetBranch.length());
			if (!_modules.contains(targetModule)) {
				// The change happened in a module that is not among the merged modules, drop the
				// change.
				continue;
			}

			if (containsAncestorOrSelf(_crossMergedPaths, targetResource)) {
				// The parent directory has been cross-branch copied. Therefore, moves within the
				// content can no longer be merged semantically.
				directMerge(pathEntry);
				continue;
			}

			if (pathEntry.getCopyPath() == null) {
				// Plain add or modify, no source specified. Merge directly.
				directMerge(pathEntry);
				continue;
			}

			{
				List<ResourceChange> sources = getMergeSources(new ResourceChange(logEntry, pathEntry), targetBranch);
				if (sources == null) {
					directMerge(pathEntry);
					continue;
				}

				ResourceChange srcChange = sources.get(sources.size() - 1);
				long srcRevision = srcChange.getChangeSet().getRevision();
				SVNLogEntryPath srcResourceChange = srcChange.getChange();

				String srcPath = srcResourceChange.getCopyPath();
				String srcBranch = getBranch(srcPath);
				String srcModule = getModuleName(srcPath, srcBranch.length());
				String srcResource = getModulePath(srcPath, srcBranch.length());

				if (!_modules.contains(srcModule)) {
					// Copied from a module that is not part of the current merge. Perform a regular
					// merge to get the content.
					directMerge(pathEntry);
					continue;
				}

				boolean isMove = isDeleted(logEntry, targetBranch + srcResource);
				
				{
					File copyFile = new File(_config.getWorkspaceRoot(), srcResource);
					File targetFile = new File(_config.getWorkspaceRoot(), targetResource);

					if (!copyFile.exists()) {
						// Locally not present, keep cross branch copy.
						directMerge(pathEntry);
						continue;
					}

					long copiedRevision = srcResourceChange.getCopyRevision();
					if (copiedRevision < srcRevision - 1) {
						// The copy potentially is a revert.
						int changeCount = getChangeCount(srcPath, copiedRevision, srcRevision);
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
							directMerge(pathEntry);
							continue;
						}
					}

					hasMoves = true;
					SvnTarget target = SvnTarget.fromFile(targetFile);

					if (srcResource.equals(targetResource)) {
						// A resource was copied to itself. This is useful for reverting a path to
						// an older version. Since such revert cannot easily be transformed to an
						// operation in the current working copy (see above), at this location, the
						// self-copy is a no-op. Only a potential modification that is done along
						// with the copy must be merged to the destination.
						SvnMerge merge = operations().createMerge();
						boolean revert = _config.getRevert();
						SVNRevision startRevision = SVNRevision.create(revert ? srcRevision : srcRevision - 1);
						SVNRevision endRevision = SVNRevision.create(revert ? srcRevision - 1 : srcRevision);

						merge.setMergeOptions(mergeOptions());
						/* Must allow as otherwise the whole workspace is checked for revisions
						 * which costs much time */
						merge.setAllowMixedRevisions(true);
						merge.setSingleTarget(target);

						SVNURL sourceUrl = SVNURL.parseURIDecoded(_config.getSvnURL() + srcPath);
						merge.setSource(SvnTarget.fromURL(sourceUrl, endRevision), false);
						merge.addRevisionRange(SvnRevisionRange.create(startRevision, endRevision));

						merge.setIgnoreAncestry(revert);
						addOperation(merge);
					} else {
						boolean alreadyDeleted = containsAncestorOrSelf(_deletedPaths, srcResource);
						if (isMove) {
							_deletedPaths.add(srcResource);
						}

						SvnCopySource copySource =
							SvnCopySource.create(SvnTarget.fromFile(copyFile, SVNRevision.BASE), SVNRevision.BASE);

						boolean moveInWorkingCopy;
						if (alreadyDeleted) {
							// At the time, the copy will be executed, the source of the move has
							// already been deleted due to other actions. Therefore, a plain copy
							// must be executed, because a move of a file in revision BASE will
							// fail.
							moveInWorkingCopy = false;
						} else {
							moveInWorkingCopy = isMove;
						}

						SvnCopy copy = operations().createCopy();
						copy.setDepth(SVNDepth.INFINITY);
						copy.setMakeParents(true);
						copy.setFailWhenDstExists(false);
						copy.setMove(moveInWorkingCopy);
						copy.addCopySource(copySource);
						copy.setSingleTarget(target);
						_operations.add(copy);
					}

					// Apply potential content changes throughout the copy chain (starting with the
					// first original intra-branch copy).
					for (int n = sources.size() - 1; n >= 0; n--) {
						ResourceChange mergedChange = sources.get(n);
						mergeContentChanges(target, mergedChange);
					}
				}
			}
		}

		if (!hasMoves) {
			// Revert singleton merges.
			for (int n = _operations.size(); n > operationCntBefore; n--) {
				_operations.remove(n - 1);
			}
		}
		return hasMoves;
	}

	private void mergeContentChanges(SvnTarget target, ResourceChange mergedChange) throws SVNException {
		long mergedRevision = mergedChange.getChangeSet().getRevision();
		SVNLogEntryPath mergedResourceChange = mergedChange.getChange();
		String origTargetPath = mergedResourceChange.getPath();

		SVNRevision revisionBefore = SVNRevision.create(mergedRevision - 1);
		SVNRevision changeRevision = SVNRevision.create(mergedRevision);

		SVNURL origTargetUrl =
			SVNURL.parseURIDecoded(_config.getSvnURL() + origTargetPath);
		SvnTarget mergeSource = SvnTarget.fromURL(origTargetUrl, changeRevision);

		SvnMerge merge = operations().createMerge();
		merge.setAllowMixedRevisions(true);
		merge.setIgnoreAncestry(true);
		merge.addRevisionRange(SvnRevisionRange.create(revisionBefore, changeRevision));
		merge.setSource(mergeSource, false);
		merge.setSingleTarget(target);
		_operations.add(merge);
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
		_clientManager.getLogClient().doLog(SVNURL.parseURIDecoded(_config.getSvnURL()),
			new String[] { path }, copiedSvnRevision, beforeMergedSvnRevision,
			afterCopiedRevision, true, false, false, 0, NO_PROPERTIES,
			counter);
		return counter.getCnt();
	}

	private void directMerge(SVNLogEntryPath pathEntry) throws SVNException {
		String path = pathEntry.getPath();
		String branch = getBranch(path);
		String resource = getModulePath(path, branch.length());
		String module = getModuleName(path, branch.length());
		ChangeType changeType = ChangeType.fromSvn(pathEntry.getType());

		if (changeType == ChangeType.ADDED || changeType == ChangeType.REPLACED) {
			_crossMergedPaths.add(resource);
		}

		// Prevent merging the whole module (if, e.g. merge info is merged for the module),
		// since this would produce conflicts with the explicitly merged moves and copies.
		if (!_modules.contains(resource) && !containsAncestorOrSelf(_deletedPaths, resource)) {
			_explicitPathChangeSetBuilder.buildMerge(pathEntry, branch, module, resource, false);

			if (changeType == ChangeType.DELETED) {
				_deletedPaths.add(resource);
			}
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
		String copyPath = mergedChange.getChange().getCopyPath();
		String copyBranch = getBranch(copyPath);
		if (!copyBranch.equals(targetBranch)) {
			while (true) {
				SVNLogEntry origEntry = loadRevision(mergedChange.getChange().getCopyRevision());
				SVNLogEntryPath origPathEntry = origEntry.getChangedPaths().get(copyPath);
				if (origPathEntry == null) {
					// Not copied directly from a copy/move changeset.
					return null;
				}

				String origCopyPath = origPathEntry.getCopyPath();
				if (origCopyPath == null) {
					// Cannot be followed to an intra-branch copy (was a plain add in the
					// original change).
					return null;
				}

				String origCopyBranch = getBranch(origCopyPath);

				mergedChange = new ResourceChange(origEntry, origPathEntry);
				result.add(mergedChange);

				copyPath = origCopyPath;

				if (origCopyBranch.equals(copyBranch)) {
					break;
				}

				copyBranch = origCopyBranch;
			}
		}

		return result;
	}

	private boolean containsAncestorOrSelf(Set<String> paths, String path) {
		while (true) {
			if (paths.contains(path)) {
				return true;
			}

			int dirSeparatorIndex = path.lastIndexOf('/');
			if (dirSeparatorIndex < 0) {
				return false;
			}

			path = path.substring(0, dirSeparatorIndex);
		}
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

	private SVNLogEntry getOriginalChange() throws SVNException {
		if (_originalEntryResolved) {
			return _originalLogEntry;
		}
		_originalEntryResolved = true;

		String targetPath = _logEntry.getChangedPaths().keySet().iterator().next();
		String targetBranch = getBranch(targetPath);

		String targetModule = getModuleName(targetPath, targetBranch.length());
		SVNURL targetModuleUrl = SVNURL.parseURIDecoded(_config.getSvnURL() + targetBranch + targetModule);

		long revision = _logEntry.getRevision();
		long originalRevision = getOriginalRevision(targetModuleUrl, revision);

		if (originalRevision > 0) {
			SVNLogEntry logEntry = loadRevision(originalRevision);

			_originalLogEntry = logEntry;
		}

		return _originalLogEntry;
	}

	private SVNLogEntry loadRevision(long revision) throws SVNException {
		SVNRevision svnRevision = SVNRevision.create(revision);

		SVNLogEntry result = _additionalRevisions.get(svnRevision);
		if (result == null) {
			class LastLogEntry implements ISVNLogEntryHandler {
				SVNLogEntry _entry;

				@Override
				public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
					_entry = logEntry;
				}

				public SVNLogEntry getLogEntry() {
					return _entry;
				}
			}

			LastLogEntry handler = new LastLogEntry();

			// Retrieve the original log entry.
			boolean stopOnCopy = false;
			boolean discoverChangedPaths = true;
			boolean includeMergedRevisions = false;
			_clientManager.getLogClient().doLog(SVNURL.parseURIDecoded(_config.getSvnURL()), ROOT,
				svnRevision, svnRevision, svnRevision,
				stopOnCopy, discoverChangedPaths, includeMergedRevisions, 0, NO_PROPERTIES, handler);

			result = handler.getLogEntry();
			_additionalRevisions.put(svnRevision, result);
		}
		return result;
	}

	private long getOriginalRevision(SVNURL url, long revision) throws SVNException {
		SVNRevision changeRevision = SVNRevision.create(revision);
		SVNRevision revisionBefore = SVNRevision.create(revision - 1);

		// Compute the diff in merge info that the current changeset produces on the
		// target module.
		Map<SVNURL, SVNMergeRangeList> mergeInfo = diffClient().doGetMergedMergeInfo(url, changeRevision);
		Map<SVNURL, SVNMergeRangeList> mergeInfoBefore = diffClient().doGetMergedMergeInfo(url, revisionBefore);

		long originalChange = Long.MAX_VALUE;
		for (Entry<SVNURL, SVNMergeRangeList> entry : mergeInfo.entrySet()) {
			SVNMergeRangeList rangeListBefore = mergeInfoBefore.get(entry.getKey());

			SVNMergeRangeList diffList;
			if (rangeListBefore != null && !rangeListBefore.isEmpty()) {
				diffList = entry.getValue().diff(rangeListBefore, true);
			} else {
				diffList = entry.getValue();
			}

			if (diffList == null || diffList.isEmpty()) {
				continue;
			}

			SVNMergeRange[] ranges = diffList.getRanges();
			if (ranges.length == 1) {
				SVNMergeRange singleRange = ranges[0];
				long startRevision = singleRange.getStartRevision();
				long endRevision = singleRange.getEndRevision();
				if (endRevision == startRevision + 1) {
					if (endRevision < originalChange) {
						originalChange = endRevision;
					}
					continue;
				}
			}

			// The merge source is not unique, multiple revisions have been merged to
			// one. The original revision cannot be determined.
			return -1;
		}

		if (originalChange == Long.MAX_VALUE) {
			// No merge info was found at all.
			return -1;
		}

		return originalChange;
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
			String changedPath = entry.getKey();
			SVNLogEntryPath pathEntry = entry.getValue();

			int moduleStartIndex = getModuleStartIndex(changedPath);
			if (moduleStartIndex < 0) {
				Log.warning("Path does not match the branch pattern: " + changedPath);
				continue;
			}

			String branch = getBranch(changedPath, moduleStartIndex);
			String moduleName = getModuleName(changedPath, moduleStartIndex);
			String modulePath = getModulePath(changedPath, moduleStartIndex);

			builder.buildMerge(pathEntry, branch, moduleName, modulePath, recordOnly);
		}
	}

	String createUrlPrefix(String branch) {
		return _config.getSvnURL() + branch;
	}

	void addMergeOperations(SVNLogEntryPath pathEntry, String resourceName, String urlPrefix, boolean recordOnly)
			throws SVNException {
		addMergeOperations(pathEntry, resourceName, urlPrefix, recordOnly, false);
	}

	void addMergeOperations(SVNLogEntryPath pathEntry, String resourceName, String urlPrefix, boolean recordOnly,
			boolean ignoreAncestry) throws SVNException {
		switch (ChangeType.fromSvn(pathEntry.getType())) {
			case DELETED: {
				if (!recordOnly) {
					addOperation(createRemove(resourceName));
				}
				break;
			}
			case ADDED: {
				if (!recordOnly) {
					addOperation(createRemoteAdd(pathEntry, resourceName));
				}
				addOperation(createModification(resourceName, urlPrefix, recordOnly, ignoreAncestry));
				break;
			}
			case REPLACED: {
				if (!recordOnly) {
					addOperation(createRemove(resourceName));
					addOperation(createRemoteAdd(pathEntry, resourceName));
				}
				addOperation(createModification(resourceName, urlPrefix, recordOnly, ignoreAncestry));
				break;
			}
			case MODIFIED: {
				addOperation(createModification(resourceName, urlPrefix, recordOnly, ignoreAncestry));
				break;
			}
		}
	}

	void addOperation(SvnOperation<?> operation) {
		_operations.add(operation);
	}

	private SvnOperation<?> createRemoteAdd(SVNLogEntryPath pathEntry, String resourceName) throws SVNException {
		SVNRevision revision;
		SvnCopySource copySource;
		if (pathEntry.getCopyPath() == null) {
			revision = SVNRevision.create(_logEntry.getRevision());
			copySource = SvnCopySource.create(
				SvnTarget.fromURL(SVNURL.parseURIDecoded(_config.getSvnURL() + pathEntry.getPath()), revision),
				revision);
		} else {
			revision = SVNRevision.create(pathEntry.getCopyRevision());
			copySource = SvnCopySource.create(
				SvnTarget.fromURL(SVNURL.parseURIDecoded(_config.getSvnURL() + pathEntry.getCopyPath()), revision),
				revision);
		}

		SvnCopy copy = operations().createCopy();
		copy.setRevision(revision);
		copy.setDepth(SVNDepth.INFINITY);
		copy.setMakeParents(true);
		copy.setFailWhenDstExists(false);
		copy.setMove(false);
		copy.addCopySource(copySource);
		copy.setSingleTarget(SvnTarget.fromFile(new File(_config.getWorkspaceRoot(), resourceName)));

		return copy;
	}

	SvnMerge createModification(String resourceName, String urlPrefix, boolean recordOnly)
			throws SVNException {
		return createModification(resourceName, urlPrefix, recordOnly, false);
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
		
		SVNURL sourceUrl = SVNURL.parseURIDecoded(urlPrefix + resourceName);
		SvnTarget source = SvnTarget.fromURL(sourceUrl, endRevision);
		merge.setSource(source, false);
		SvnRevisionRange range = SvnRevisionRange.create(startRevision, endRevision);
		merge.addRevisionRange(range);
		
		merge.setIgnoreAncestry(revert || ignoreAncestry);
		return merge;
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

	private String getBranch(String changedPath) {
		return getBranch(changedPath, getModuleStartIndex(changedPath));
	}

	private static String getBranch(String changedPath, int moduleStartIndex) {
		return changedPath.substring(0, moduleStartIndex);
	}

	abstract class MergeBuilder {

		public abstract void buildMerge(SVNLogEntryPath pathEntry, String branch, String moduleName, String modulePath,
				boolean recordOnly) throws SVNException;

	}

	class CompleteModuleChangeSetBuilder extends MergeBuilder {

		Set<String> _mergedModules = new HashSet<>();

		public CompleteModuleChangeSetBuilder() {
			super();
		}

		@Override
		public void buildMerge(SVNLogEntryPath pathEntry, String branch, String moduleName, String modulePath,
				boolean recordOnly) throws SVNException {
			if (!_modules.contains(moduleName)) {
				return;
			}
			if (_mergedModules.contains(moduleName)) {
				return;
			}
			_mergedModules.add(moduleName);

			addOperation(createModification(moduleName, createUrlPrefix(branch), recordOnly));
		}
	}

	static String getModuleName(String changedPath, int moduleStartIndex) {
		int moduleEndIndex = changedPath.indexOf(Utils.SVN_SERVER_PATH_SEPARATOR, moduleStartIndex);
		if (moduleEndIndex < 0) {
			moduleEndIndex = changedPath.length();
		}
		return changedPath.substring(moduleStartIndex, moduleEndIndex);
	}

	class PartialChangeSetBuilder extends MergeBuilder {

		private Set<String> _includePaths;

		public PartialChangeSetBuilder(Set<String> includePaths) {
			_includePaths = includePaths;
		}

		@Override
		public void buildMerge(SVNLogEntryPath pathEntry, String branch, String moduleName, String modulePath,
				boolean recordOnly) throws SVNException {
			if (!_includePaths.contains(modulePath)) {
				// Skip path.
				return;
			}

			String urlPrefix = createUrlPrefix(branch);
			addMergeOperations(pathEntry, modulePath, urlPrefix, recordOnly, true);
		}
	}

	class ExplicitPathChangeSetBuilder extends MergeBuilder {
		@Override
		public void buildMerge(SVNLogEntryPath pathEntry, String branch, String moduleName, String modulePath,
				boolean recordOnly) throws SVNException {
			String urlPrefix = createUrlPrefix(branch);
			addMergeOperations(pathEntry, modulePath, urlPrefix, recordOnly);
		}

	}

	static String getModulePath(String changedPath, int moduleStartIndex) {
		return changedPath.substring(moduleStartIndex);
	}

}
