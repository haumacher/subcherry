/*
 * SubCherry - Cherry Picking with Trac and Subversion
 * Copyright (C) 2014 Bernhard Haumacher and others
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.subcherry.merge;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import com.subcherry.AdditionalRevision;
import com.subcherry.MergeConfig;
import com.subcherry.repository.command.Client;
import com.subcherry.repository.command.ClientManager;
import com.subcherry.repository.command.Command;
import com.subcherry.repository.command.CommandVisitor;
import com.subcherry.repository.command.OperationFactory;
import com.subcherry.repository.command.copy.Copy;
import com.subcherry.repository.command.copy.CopySource;
import com.subcherry.repository.command.log.LogEntryHandler;
import com.subcherry.repository.command.merge.ConflictAction;
import com.subcherry.repository.command.merge.ConflictReason;
import com.subcherry.repository.command.merge.Merge;
import com.subcherry.repository.command.merge.MergeOperation;
import com.subcherry.repository.command.merge.ScheduledTreeConflict;
import com.subcherry.repository.command.wc.LocalDelete;
import com.subcherry.repository.command.wc.LocalMkDir;
import com.subcherry.repository.core.ChangeType;
import com.subcherry.repository.core.Depth;
import com.subcherry.repository.core.LogEntry;
import com.subcherry.repository.core.LogEntryPath;
import com.subcherry.repository.core.NodeKind;
import com.subcherry.repository.core.RepositoryException;
import com.subcherry.repository.core.RepositoryURL;
import com.subcherry.repository.core.Revision;
import com.subcherry.repository.core.RevisionRange;
import com.subcherry.repository.core.Target;
import com.subcherry.repository.core.Target.FileTarget;
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

	private List<Command> _operations;

	private List<Command> _operationsSetup;

	private List<Command> _operationsCleanup;

	private ClientManager _clientManager;

	private Map<Revision, LogEntry> _additionalRevisions = new HashMap<>();

	private final VirtualFS _virtualFs;

	private Set<String> _crossMergedDirectories;

	private Set<String> _touchedResources;

	private final PathParser _paths;

	private ResourceMapping _mapping;

	private int _id = 1;

	public MergeHandler(ClientManager clientManager, MergeConfig config, PathParser paths, Set<String> modules) {
		super(config);
		_clientManager = clientManager;
		_paths = paths;
		_modules = modules;
		_virtualFs = new VirtualFS(config.getWorkspaceRoot());
		_mapping = ResourceMapping.create(config);
	}

	private OperationFactory operations() {
		return client().getOperationsFactory();
	}

	private Client client() {
		return _clientManager.getClient();
	}

	public MergeOperation parseMerge(LogEntry logEntry) throws RepositoryException {
		_operations = new ArrayList<>();
		_operationsSetup = new ArrayList<>();
		_operationsCleanup = new ArrayList<>();
		_virtualFs.clear();
		_crossMergedDirectories = new HashSet<>();
		_touchedResources = new HashSet<>();

		// It is not probable that multiple change sets require the same copy revision.
		_additionalRevisions.clear();

		buildOperations(logEntry);
		resolveOperationDependencies();

		_operationsSetup.addAll(_operations);
		_operationsSetup.addAll(_operationsCleanup);

		return new MergeOperation(logEntry.getRevision(), _operationsSetup, _touchedResources);
	}

	private void buildOperations(LogEntry logEntry) throws RepositoryException {
		AdditionalRevision additionalInfo = _config.getAdditionalRevisions().get(logEntry.getRevision());
		Set<String> includePaths;
		if (additionalInfo != null) {
			includePaths = additionalInfo.getIncludePaths();
		} else {
			includePaths = null;
		}
		
		if (includePaths == null) {
			boolean hasMoves = _config.getSemanticMoves() && (!_config.getRevert()) && handleCopies(logEntry);
			if (hasMoves) {
				addRecordOnly(logEntry, new CompleteModuleChangeSetBuilder());
			} else {
				addMerges(logEntry, new CompleteModuleChangeSetBuilder());
			}
		} else {
			addMerges(logEntry, new PartialChangeSetBuilder(includePaths));
		}
	}

	private boolean hasNoCopies(LogEntry logEntry) {
		for (LogEntryPath pathEntry : logEntry.getChangedPaths().values()) {
			if (pathEntry.getCopyPath() != null) {
				// There is potentially a change that must be treated especially.
				return false;
			}

			if (mapResource(_paths.parsePath(pathEntry)) != null) {
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
	private boolean handleCopies(LogEntry logEntry) throws RepositoryException {
		if (hasNoCopies(logEntry)) {
			// Optimization.
			return false;
		}

		boolean hasMoves = false;

		List<LogEntryPath> entries = pathOrder(logEntry.getChangedPaths().values());
		for (int index = 0, cnt = entries.size(); index < cnt; index++) {
			LogEntryPath svnPathEntry = entries.get(index);

			String pathName = svnPathEntry.getPath();
			if (!usePath(pathName)) {
				hasMoves = true;
				continue;
			}

			final Path originalTarget = _paths.parsePath(svnPathEntry);
			final Path target = mapPath(originalTarget);
			boolean isMapped = target != originalTarget;
			if (isMapped) {
				hasMoves = true;
			}

			if (!_modules.contains(target.getModule())) {
				if (_modules.contains(originalTarget.getModule())) {
					/* Rewrite rule from a merge module to a non existing module. The rewrite rules
					 * are inconsistent. */
					ScheduledTreeConflict conflict = operations().newScheduledTreeConflict();
					conflict.setAction(toAction(target.getPathEntry().getType()));
					conflict.setReason(ConflictReason.MISSING);
					conflict.setTarget(Target.fromFile(new File(_config.getWorkspaceRoot(), target.getResource())));
					addOperation(target.getResource(), conflict);
				}
				/* The change happened in a module that is not among the merged modules, drop the
				 * change. */
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
						LogEntryPath childEntry = entries.get(childIndex);
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
						Command mkDir = mkDir(target.getResource());
						addOperation(target.getResource(), mkDir);
						continue;
					}
				}

				// Plain add or modify, no source specified. Merge directly.
				directMerge(logEntry.getRevision(), originalTarget, target);
				continue;
			}

			{
				List<ResourceChange> originalSources =
					getMergeSources(new ResourceChange(logEntry, originalTarget), target.getBranch());

				ResourceChange srcChangeOrig = originalSources.get(originalSources.size() - 1);
				long srcRevision = srcChangeOrig.getChangeSet().getRevision();
				Path srcResourceChangeOrig = srcChangeOrig.getChange();

				Path srcPathOrig = srcResourceChangeOrig.getCopyPath();
				Path srcPathMapped = mapPath(srcPathOrig);
				String srcModule = srcPathOrig.getModule();
				String srcResourceMapped = srcPathMapped.getResource();

				boolean intraBranchCopy = srcResourceChangeOrig.getBranch().equals(srcPathOrig.getBranch());
				{
					File srcFile;
					boolean srcExistsBefore;
					if (intraBranchCopy && _modules.contains(srcModule)) {
						srcFile = new File(_config.getWorkspaceRoot(), srcResourceMapped);
						srcExistsBefore = srcFile.exists();
					} else {
						// Copied from a module that is not part of the current merge. Perform a
						// regular cross-branch copy of the content.
						srcFile = null;
						srcExistsBefore = false;
					}

					File targetFile = new File(_config.getWorkspaceRoot(), target.getResource());

					long copiedRevision = srcResourceChangeOrig.getCopyRevision();
					List<LogEntry> intermediateChanges = Collections.emptyList();
					if (srcExistsBefore) {
						if (copiedRevision < srcRevision - 1) {
							// The copy potentially is a revert.
							intermediateChanges = getChanges(srcPathOrig.getPath(), copiedRevision, srcRevision);
						}
					}

					hasMoves = true;
					Target svnTarget = Target.fromFile(targetFile, Revision.WORKING);

					boolean removeBeforeMerge;
					if (target.getType() == ChangeType.REPLACED) {
						// Delete target before re-creating. Otherwise copy will fail.
						removeBeforeMerge = existsWhenMerged(target.getResource());
					} else {
						removeBeforeMerge = false;
					}

					boolean noOpCopy = srcExistsBefore && srcResourceMapped.equals(target.getResource());
					if (!noOpCopy) {
						if (removeBeforeMerge) {
							addRemove(target.getResource());
						}

						CopySource copySource;
						if (!srcExistsBefore) {
							// Keep a remote cross branch copy.
							RepositoryURL srcUrl = svnUrl(_config.getSvnURL() + srcResourceChangeOrig.getCopyPath());
							Revision copiedSvnRevision = Revision.create(copiedRevision);
							
							copySource =
								CopySource.create(Target.fromURL(srcUrl, copiedSvnRevision), copiedSvnRevision);
						} else {
							CopySource origSrc =
								CopySource.create(Target.fromFile(srcFile, Revision.WORKING), Revision.WORKING);

							boolean isChanged = isChanged(logEntry, srcPathMapped);
							if (isChanged) {
								// There is a change to the source resource of the copy within the
								// same commit. Since the original copy was done from the original
								// (unchanged version of the file), the source file has to be backed
								// up before applying the regular merges. This allows to copy from
								// the (backed up) working copy version of the source file without
								// already copying the changes that are also applied to the source
								// within the same commit.
								File backupFile = backupFor(srcPathMapped, srcFile);
								Target backupTarget = Target.fromFile(backupFile, Revision.WORKING);

								Copy backup = operations().createCopy();
								backup.setMakeParents(true);
								backup.setFailWhenDstExists(true);
								backup.setMove(false);
								backup.setCopySource(origSrc);
								backup.setTarget(backupTarget);

								addOperationSetup(backup);

								LocalDelete cleanup = operations().createLocalFileDelete();
								cleanup.setTarget(backupTarget);

								addOperationCleanup(cleanup);

								copySource =
									CopySource.create(Target.fromFile(backupFile, Revision.WORKING), Revision.WORKING);
							} else {
								copySource = origSrc;
							}
						}

						Copy copy = operations().createCopy();
						copy.setMakeParents(true);
						// Note: Must not ignore existance: If a directory is copied, and the
						// destination path exists, the directory is copied into the existing
						// directory, instead of its content.
						copy.setFailWhenDstExists(true);
						// It is hard to determine, whether the source must be delete at the time
						// the destination of the move is copied. Just handle the deletion at the
						// time, the original deletion occurred (if it is de-facto a move).
						copy.setMove(false);
						copy.setCopySource(copySource);
						copy.setTarget(svnTarget);
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

						Revision startRevision = Revision.create(srcRevision - 1);
						Revision endRevision = Revision.create(copiedRevision);
						Revision pegRevision = startRevision;

						String mergeSourcePath = srcResourceChangeOrig.getCopyPath().getPath();
						Target mergeSource =
							Target.fromURL(svnUrl(_config.getSvnURL() + mergeSourcePath), pegRevision);

						Merge merge = operations().createMerge();
						merge.setAllowMixedRevisions(true);
						merge.setIgnoreAncestry(true);
						merge.addRevisionRange(RevisionRange.create(startRevision, endRevision));
						merge.setSource(mergeSource, false);
						merge.setTarget(svnTarget);
						addOperation(target.getResource(), merge);

						for (LogEntry intermediateChange : intermediateChanges) {
							for (LogEntryPath changedPathEntry : intermediateChange.getChangedPaths().values()) {
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
					for (int n = originalSources.size() - 1; n >= 0; n--) {
						ResourceChange mergedChange = originalSources.get(n);
						Merge merge = mergeContentChanges(svnTarget, mergedChange);
						if (n == 0) {
							merge.setDepth(Depth.EMPTY);
						}
						addOperation(target.getResource(), merge);
					}
				}
			}
		}

		if (!hasMoves) {
			// Revert singleton merges.
			_operations.clear();
			_operationsSetup.clear();
			_operationsCleanup.clear();
			_touchedResources.clear();
			_virtualFs.clear();
			_crossMergedDirectories.clear();
		}
		return hasMoves;
	}

	private boolean isChanged(LogEntry logEntry, Path path) {
		String checkPath = path.getPath();
		int resourceStart = path.getPath().length() - path.getResource().length();
		while (true) {
			LogEntryPath srcChange = logEntry.getChangedPaths().get(checkPath);
			boolean isChanged = srcChange != null;
			if (isChanged) {
				return true;
			}

			int sepIdx = checkPath.lastIndexOf('/');
			if (sepIdx < resourceStart) {
				break;
			}

			checkPath = checkPath.substring(0, sepIdx);
		}
		return false;
	}

	protected File backupFor(Path srcPathMapped, File srcFile) {
		File backupDir = new File(_config.getWorkspaceRoot(), srcPathMapped.getModule());
		return new File(backupDir, srcFile.getName() + ".$$$" + newId());
	}

	private String newId() {
		return Integer.toString(_id++);
	}

	private ConflictAction toAction(ChangeType type) {
		switch (type) {
			case ADDED:
				return ConflictAction.ADDED;
			case DELETED:
				return ConflictAction.DELETED;
			case MODIFIED:
				return ConflictAction.EDITED;
			case REPLACED:
				return ConflictAction.ADDED;
		}
		throw new IllegalArgumentException("No such type: " + type);
	}

	private Path mapPath(Path orig) {
		String mappedResource = mapResource(orig);
		if (mappedResource == null) {
			return orig;
		}
		return new Path(orig, mappedResource);
	}

	private String mapResource(Path orig) {
		return _mapping.map(orig.getResource());
	}

	private static boolean isSubPath(String subPath, String parentPath) {
		int parentLength = parentPath.length();
		return subPath.length() > parentLength && subPath.startsWith(parentPath) && subPath.charAt(parentLength) == '/';
	}

	private Command mkDir(String resource) {
		LocalMkDir localMkDir = operations().newLocalMkDir();
		localMkDir.setTarget(Target.fromFile(new File(_config.getWorkspaceRoot(), resource)));
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

	private Merge mergeContentChanges(Target target, ResourceChange mergedChange)
			throws RepositoryException {
		LogEntry mergedChangeSet = mergedChange.getChangeSet();
		long mergedRevision = mergedChangeSet.getRevision();
		Path mergedResourceChange = mergedChange.getChange();
		String origTargetPath = mergedResourceChange.getPath();

		if (mergedResourceChange.getPathEntry().getKind() == NodeKind.DIR) {
			String dirPrefix = origTargetPath + '/';
			for (LogEntryPath contentChange : mergedChangeSet.getChangedPaths().values()) {
				String contentPathName = contentChange.getPath();
				if (contentPathName.startsWith(dirPrefix)) {
					Path contentPath = _paths.parsePath(contentChange);
					addCommitResource(contentPath.getResource());
				}
			}
		}

		Revision revisionBefore = Revision.create(mergedRevision - 1);
		Revision changeRevision = Revision.create(mergedRevision);

		RepositoryURL origTargetUrl = svnUrl(_config.getSvnURL() + origTargetPath);
		Target mergeSource = Target.fromURL(origTargetUrl, changeRevision);

		Merge merge = operations().createMerge();
		merge.setAllowMixedRevisions(true);
		merge.setIgnoreAncestry(true);
		merge.addRevisionRange(RevisionRange.create(revisionBefore, changeRevision));
		merge.setSource(mergeSource, false);
		merge.setTarget(target);
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
	private List<LogEntry> getChanges(String path, long copiedRevision, long mergedRevision) throws RepositoryException {
		class Counter implements LogEntryHandler {
			private final List<LogEntry> _changes = new ArrayList<>();

			@Override
			public void handleLogEntry(LogEntry intermediateChange) throws RepositoryException {
				_changes.add(intermediateChange);
			}

			public List<LogEntry> getChanges() {
				return _changes;
			}
		}
		Counter counter = new Counter();
		Revision beforeMergedSvnRevision = Revision.create(mergedRevision - 1);
		Revision afterCopiedRevision = Revision.create(copiedRevision + 1);
		Revision copiedSvnRevision = Revision.create(copiedRevision);
		_clientManager.getClient().log(svnUrl(_config.getSvnURL()),
			new String[] { path }, copiedSvnRevision, beforeMergedSvnRevision,
			afterCopiedRevision, true, true, false, 0, NO_PROPERTIES,
			counter);
		return counter.getChanges();
	}

	private void directMerge(long revision, Path source, Path target) throws RepositoryException {
		String resource = target.getResource();
		ChangeType changeType = target.getType();

		if (changeType == ChangeType.ADDED || changeType == ChangeType.REPLACED) {
			if (target.getKind() == NodeKind.DIR) {
				_crossMergedDirectories.add(resource);
			}
		}

		// Prevent merging the whole module (if, e.g. merge info is merged for the module),
		// since this would produce conflicts with the explicitly merged moves and copies.
		if (!_modules.contains(resource)) {
			buildResourceMerge(revision, source, target, false, true);
		}
	}

	private void buildResourceMerge(long revision, Path src, Path target, boolean recordOnly, boolean ignoreAncestry)
			throws RepositoryException {
		String urlPrefix = createUrlPrefix(src.getBranch());
		if (src.getKind() == NodeKind.DIR && src.getType() == ChangeType.MODIFIED) {
			addOperation(target.getResource(), createModification(revision, src.getResource(), target.getResource(), urlPrefix, recordOnly, ignoreAncestry, true));
		} else {
			addMergeOperations(revision, src, target.getResource(), urlPrefix, recordOnly, ignoreAncestry);
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
			throws RepositoryException {
		ArrayList<ResourceChange> result = new ArrayList<>();
		result.add(origChange);

		ResourceChange mergedChange = origChange;
		Path copyPath = mergedChange.getChange().getCopyPath();
		String copyBranch = copyPath.getBranch();
		if (!copyBranch.equals(targetBranch)) {
			while (true) {
				LogEntry origEntry = loadRevision(mergedChange.getChange().getCopyRevision());
				LogEntryPath origPathEntry = origEntry.getChangedPaths().get(copyPath.getPath());
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

	private List<LogEntryPath> pathOrder(Collection<LogEntryPath> values) {
		ArrayList<LogEntryPath> result = new ArrayList<>(values);
		Collections.sort(result, new Comparator<LogEntryPath>() {
			@Override
			public int compare(LogEntryPath p1, LogEntryPath p2) {
				return p1.getPath().compareTo(p2.getPath());
			}
		});
		return result;
	}

	private LogEntry loadRevision(long revision) throws RepositoryException {
		Revision svnRevision = Revision.create(revision);

		LogEntry result = _additionalRevisions.get(svnRevision);
		if (result == null) {
			LastLogEntry handler = new LastLogEntry();

			// Retrieve the original log entry.
			boolean stopOnCopy = false;
			boolean discoverChangedPaths = true;
			boolean includeMergedRevisions = false;
			_clientManager.getClient().log(svnUrl(_config.getSvnURL()), ROOT,
				svnRevision, svnRevision, svnRevision,
				stopOnCopy, discoverChangedPaths, includeMergedRevisions, 0, NO_PROPERTIES, handler);

			result = handler.getLogEntry();
			_additionalRevisions.put(svnRevision, result);
		}
		return result;
	}

	private void addRecordOnly(LogEntry logEntry, MergeBuilder builder) throws RepositoryException {
		createMerges(logEntry, builder, true);
	}

	private void addMerges(LogEntry logEntry, MergeBuilder builder) throws RepositoryException {
		createMerges(logEntry, builder, false);
	}

	private void createMerges(LogEntry logEntry, MergeBuilder builder, boolean recordOnly) throws RepositoryException {
		Map<String, LogEntryPath> changedPaths = logEntry.getChangedPaths();
		for (Entry<String, LogEntryPath> entry : changedPaths.entrySet()) {
			LogEntryPath pathEntry = entry.getValue();
			if (!usePath(pathEntry.getPath())) {
				continue;
			}

			Path changedPath = _paths.parsePath(pathEntry);
			if (changedPath.getBranch() == null) {
				Log.warning("Path does not match the branch pattern: " + changedPath);
				continue;
			}

			builder.buildMerge(logEntry.getRevision(), changedPath, recordOnly, _config.getIgnoreMergeInfo());
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

	void addMergeOperations(long revision, Path srcPath, String targetResource, String urlPrefix, boolean recordOnly,
			boolean ignoreAncestry) throws RepositoryException {
		switch (srcPath.getType()) {
			case DELETED: {
				if (!recordOnly) {
					addRemove(targetResource);
				}
				break;
			}
			case ADDED: {
				if (!recordOnly) {
					addRemoteAdd(revision, srcPath, targetResource);
				}
				addModification(revision, srcPath.getResource(), targetResource, urlPrefix, recordOnly, ignoreAncestry);
				break;
			}
			case REPLACED: {
				if (!recordOnly) {
					addRemove(targetResource);
					addRemoteAdd(revision, srcPath, targetResource);
				}
				addModification(revision, srcPath.getResource(), targetResource, urlPrefix, recordOnly, ignoreAncestry);
				break;
			}
			case MODIFIED: {
				addModification(revision, srcPath.getResource(), targetResource, urlPrefix, recordOnly, ignoreAncestry);
				break;
			}
		}
	}

	private void addOperationSetup(Command operation) {
		_operationsSetup.add(operation);
	}

	private void addOperationCleanup(Command operation) {
		_operationsCleanup.add(operation);
	}

	private void addOperation(String targetResource, Command operation) {
		_operations.add(operation);
		addCommitResource(targetResource);
	}

	void addCommitResource(String resource) {
		_touchedResources.add(resource);
	}

	private void addRemoteAdd(long revision, Path srcPath, String targetResource) throws RepositoryException {
		addOperation(targetResource, createRemoteAdd(revision, srcPath, targetResource));
	}

	private Command createRemoteAdd(long srcRevision, Path srcPath, String targetResource) throws RepositoryException {
		File targetFile = new File(_config.getWorkspaceRoot(), targetResource);

		_virtualFs.add(targetResource);

		Revision revision;
		CopySource copySource;
		if (srcPath.getCopyPath() == null) {
			revision = Revision.create(srcRevision);
			copySource = CopySource.create(
				Target.fromURL(svnUrl(_config.getSvnURL() + srcPath.getPath()), revision),
				revision);
		} else {
			revision = Revision.create(srcPath.getCopyRevision());
			copySource = CopySource.create(
				Target.fromURL(svnUrl(_config.getSvnURL() + srcPath.getCopyPath()), revision),
				revision);
		}

		Copy copy = operations().createCopy();
		copy.setRevision(revision);
		copy.setMakeParents(false);
		copy.setFailWhenDstExists(true);
		copy.setMove(false);
		copy.setCopySource(copySource);
		copy.setTarget(Target.fromFile(targetFile));

		return copy;
	}

	private String parent(String resource) {
		int dirSeparatorIndex = resource.lastIndexOf('/');
		if (dirSeparatorIndex < 0) {
			return "";
		}

		return resource.substring(0, dirSeparatorIndex);
	}

	void addModification(long revision, String resource, String urlPrefix, boolean recordOnly, boolean ignoreAncestry)
			throws RepositoryException {
		addOperation(resource, createModification(revision, resource, resource, urlPrefix, recordOnly, ignoreAncestry, false));
	}

	void addModification(long revision, String srcResource, String targetResource, String urlPrefix, boolean recordOnly, boolean ignoreAncestry)
			throws RepositoryException {
		addOperation(targetResource, createModification(revision, srcResource, targetResource, urlPrefix, recordOnly, ignoreAncestry, false));
	}
	
	Command createModification(long revision, String srcResourceName, String targetResourceName,
			String urlPrefix,
			boolean recordOnly, boolean ignoreAncestry, boolean empty) throws RepositoryException {
		File targetFile = new File(_config.getWorkspaceRoot(), targetResourceName);
		if (!existsWhenMerged(targetResourceName)) {
			ScheduledTreeConflict conflict = operations().newScheduledTreeConflict();
			conflict.setTarget(Target.fromFile(targetFile));
			conflict.setAction(ConflictAction.EDITED);
			conflict.setReason(ConflictReason.MISSING);
			return conflict;
		}
		
		Merge merge = operations().createMerge();
		merge.setRecordOnly(recordOnly);
		if (empty || recordOnly) {
			/* Set depth empty to avoid recording merge information on single files. Otherwise
			 * property changes on files that are not touched by the merge. */
			merge.setDepth(Depth.EMPTY);
		}
		boolean revert = _config.getRevert();
		Revision startRevision = Revision.create(revert ? revision : revision - 1);
		Revision endRevision = Revision.create(revert ? revision - 1 : revision);
		
		/* Must allow as otherwise the whole workspace is checked for revisions which costs much
		 * time */
		merge.setAllowMixedRevisions(true);
		Target target = Target.fromFile(targetFile);
		merge.setTarget(target);
		
		RepositoryURL sourceUrl = svnUrl(urlPrefix + srcResourceName);
		Target source = Target.fromURL(sourceUrl, endRevision);
		merge.setSource(source, false);
		RevisionRange range = RevisionRange.create(startRevision, endRevision);
		merge.addRevisionRange(range);
		
		merge.setIgnoreAncestry(revert || ignoreAncestry);
		return merge;
	}

	private void addRemove(String targetResource) {
		addOperation(targetResource, createRemove(targetResource));
		_virtualFs.delete(targetResource);
	}

	private Command createRemove(String resourceName) {
		File targetFile = new File(_config.getWorkspaceRoot(), resourceName);

		LocalDelete remove = operations().createLocalFileDelete();
		remove.setTarget(Target.fromFile(targetFile));
		return remove;
	}

	private void resolveOperationDependencies() {
		List<Command> originalOperations = _operations;

		DelayDeletes delayDeletes = new DelayDeletes();
		for (Command command : originalOperations) {
			command.visit(delayDeletes, null);
		}
		List<Command> delayedDeletes = delayDeletes.getResult();

		Collections.reverse(delayedDeletes);
		InsertBackup insertBackup = new InsertBackup();
		for (Command command : delayedDeletes) {
			command.visit(insertBackup, null);
		}

		_operations = insertBackup.getResult();
	}

	abstract class CommandAnalyzer implements CommandVisitor<Void, Void> {

		protected abstract void createDependency(File newFile, Command command);

		protected abstract void copyDependency(File sourceFile, File destinationFile, Copy command);

		protected abstract void deleteDependency(File oldFile, LocalDelete command);

		protected abstract void modifyDependency(File file, Command command);

		@Override
		public Void visitCopy(Copy command, Void arg) {
			Target destination = command.getTarget();

			Target source = command.getCopySource().getTarget();
			boolean local = source instanceof FileTarget;

			if (local) {
				copyDependency(file(source), file(destination), command);
			} else {
				createDependency(file(destination), command);
			}

			return null;
		}

		@Override
		public Void visitMerge(Merge command, Void arg) {
			modifyDependency(file(command.getTarget()), command);
			return null;
		}

		@Override
		public Void visitLocalDelete(LocalDelete command, Void arg) {
			deleteDependency(file(command.getTarget()), command);
			return null;
		}

		@Override
		public Void visitLocalMkDir(LocalMkDir command, Void arg) {
			createDependency(file(command.getTarget()), command);
			return null;
		}

		@Override
		public Void visitScheduledTreeConflict(ScheduledTreeConflict command, Void arg) {
			modifyDependency(file(command.getTarget()), command);
			return null;
		}

		private File file(Target target) {
			return ((FileTarget) target).getFile();
		}
	}

	abstract class CommandRewriter extends CommandAnalyzer {

		private List<Command> _result = new ArrayList<>();

		protected void add(Command command) {
			_result.add(command);
		}

		public List<Command> getResult() {
			return _result;
		}

	}

	/**
	 * {@link CommandRewriter} that delays deletes until the deleted path is created again, or until
	 * the very end of the operations.
	 */
	final class DelayDeletes extends CommandRewriter {
		private Map<File, Command> _deletes = new LinkedHashMap<>();

		@Override
		protected void createDependency(File newFile, Command command) {
			addDelete(newFile);
			add(command);
		}

		@Override
		protected void copyDependency(File sourceFile, File destinationFile, Copy command) {
			addDelete(destinationFile);
			add(command);
		}

		@Override
		protected void deleteDependency(File oldFile, LocalDelete command) {
			_deletes.put(oldFile, command);
		}

		@Override
		protected void modifyDependency(File file, Command command) {
			addDelete(file);
			add(command);
		}

		private void addDelete(File file) {
			Command delete = _deletes.remove(file);
			if (delete != null) {
				add(delete);
			}
		}

		@Override
		public List<Command> getResult() {
			List<Command> result = super.getResult();
			result.addAll(_deletes.values());
			_deletes.clear();
			return result;
		}

	}

	class InsertBackup extends CommandRewriter {

		private Map<File, List<Copy>> _dependencies = new HashMap<>();

		private List<Command> _cleanup = new ArrayList<>();

		@Override
		protected void createDependency(File newFile, Command command) {
			add(command);
		}

		@Override
		protected void copyDependency(File sourceFile, File destinationFile, Copy command) {
			List<Copy> copies = _dependencies.get(sourceFile);
			if (copies == null) {
				copies = new ArrayList<>();
				_dependencies.put(sourceFile, copies);
			}
			copies.add(command);
			add(command);
		}

		@Override
		protected void deleteDependency(File oldFile, LocalDelete command) {
			add(command);

			List<Copy> copies = _dependencies.remove(oldFile);
			if (copies != null) {
				// The deleted file is referenced later on in a copy. Therefore, a backup must be
				// created.
				Copy backup = operations().createCopy();
				backup.setCopySource(CopySource.create(Target.fromFile(oldFile, Revision.WORKING), Revision.WORKING));
				backup.setFailWhenDstExists(true);
				backup.setMakeParents(false);
				backup.setMove(false);

				int id = 0;
				File tmpFile;
				do {
					tmpFile = new File(oldFile.getParentFile(), oldFile.getName() + (id > 0 ? "." + id : "") + ".tmp");
				} while (tmpFile.exists());

				backup.setTarget(Target.fromFile(tmpFile));
				add(backup);

				// Rewrite copies to use backup.
				for (Copy copy : copies) {
					copy.setCopySource(CopySource.create(Target.fromFile(tmpFile, Revision.WORKING), Revision.WORKING));
				}

				LocalDelete cleanup = operations().createLocalFileDelete();
				cleanup.setTarget(Target.fromFile(tmpFile, Revision.WORKING));
				_cleanup.add(cleanup);
			}
		}

		@Override
		protected void modifyDependency(File file, Command command) {
			add(command);
		}

		@Override
		public List<Command> getResult() {
			List<Command> result = super.getResult();
			Collections.reverse(result);
			result.addAll(_cleanup);
			_cleanup.clear();
			return result;
		}

	}

	abstract class MergeBuilder {

		public abstract void buildMerge(long revision, Path path, boolean recordOnly, boolean ignoreAncestry) throws RepositoryException;

	}

	class CompleteModuleChangeSetBuilder extends MergeBuilder {

		Set<String> _mergedModules = new HashSet<>();

		public CompleteModuleChangeSetBuilder() {
			super();
		}

		@Override
		public void buildMerge(long revision, Path path, boolean recordOnly, boolean ignoreAncestry) throws RepositoryException {
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
		public void buildMerge(long revision, Path path, boolean recordOnly, boolean ignoreAncestry) throws RepositoryException {
			if (!_includePaths.contains(path.getResource())) {
				// Skip path.
				return;
			}

			String urlPrefix = createUrlPrefix(path.getBranch());
			addMergeOperations(revision, path, path.getResource(), urlPrefix, recordOnly, true);
		}
	}

	private static RepositoryURL svnUrl(String svnURL) throws RepositoryException {
		return RepositoryURL.parse(svnURL);
	}

}
