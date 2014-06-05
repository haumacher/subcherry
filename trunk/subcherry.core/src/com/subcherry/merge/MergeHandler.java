package com.subcherry.merge;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
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

	private final Set<String> _modules;

	private SVNLogEntry _logEntry;

	private List<SvnOperation<?>> _operations;

	private SVNClientManager _clientManager;

	private SVNLogEntry _originalLogEntry;

	private boolean _originalEntryResolved;

	private Map<SVNRevision, SVNLogEntry> _additionalRevisions = new HashMap<>();

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
			Set<String> excludePaths = handleMoves();

			if (excludePaths.isEmpty()) {
				addMerges(new CompleteModuleChangeSetBuilder(_modules));
			} else {
				// Prevent merging the whole module (if, e.g. merge info is merged for the module),
				// since this would produce conflicts with the explicitly merged moves and copies.
				excludePaths.addAll(_modules);

				addMerges(new ExplicitPathChangeSetBuilder(_modules, excludePaths));
				addRecordOnly(new CompleteModuleChangeSetBuilder(_modules));
			}
		} else {
			addMerges(new PartialChangeSetBuilder(includePaths));
		}
	}

	private Set<String> handleMoves() throws SVNException {
		Set<String> excludePaths = new HashSet<>();

		allPaths:
		for (SVNLogEntryPath pathEntry : _logEntry.getChangedPaths().values()) {
			String srcPath = pathEntry.getCopyPath();
			if (srcPath != null) {
				String targetPath = pathEntry.getPath();
				String targetBranch = getBranch(targetPath);
				String targetModule = getModuleName(targetPath, targetBranch.length());
				String targetResource = getModulePath(targetPath, targetBranch.length());
				
				SVNLogEntry logEntry = _logEntry;
				String srcBranch = getBranch(srcPath);
				String origTargetPath = targetPath;
				if (!srcBranch.equals(targetBranch)) {
					SVNLogEntryPath mergedPathEntry = pathEntry;
					while (true) {
						SVNLogEntry origEntry = loadRevision(mergedPathEntry.getCopyRevision());
						SVNLogEntryPath origPathEntry = origEntry.getChangedPaths().get(srcPath);
						if (origPathEntry == null) {
							// Not copied directly from a copy/move changeset.
							continue allPaths;
						}

						String srcPath2 = origPathEntry.getCopyPath();
						if (srcPath2 == null) {
							// Cannot be followed to an intra-branch copy (was a plain add in the
							// original change).
							continue allPaths;
						}

						String srcBranch2 = getBranch(srcPath2);

						logEntry = origEntry;
						mergedPathEntry = origPathEntry;
						srcPath = srcPath2;

						if (srcBranch2.equals(srcBranch)) {
							origTargetPath = origPathEntry.getPath();
							break;
						}

						srcBranch = srcBranch2;
					}
				}

				long revision = logEntry.getRevision();

				String srcModule = getModuleName(srcPath, srcBranch.length());
				String srcResource = getModulePath(srcPath, srcBranch.length());

				boolean isMove = isDeleted(_logEntry, targetBranch + srcResource);
				
				if (_modules.contains(targetModule) && _modules.contains(srcModule)) {
					if (isMove) {
						excludePaths.add(srcResource);
					}
					excludePaths.add(targetResource);

					File copyFile = new File(_config.getWorkspaceRoot(), srcResource);
					File targetFile = new File(_config.getWorkspaceRoot(), targetResource);

					SvnCopySource copySource =
						SvnCopySource.create(SvnTarget.fromFile(copyFile), SVNRevision.WORKING);
					SvnTarget target = SvnTarget.fromFile(targetFile);

					SvnCopy copy = operations().createCopy();
					copy.setRevision(SVNRevision.WORKING);
					copy.setDepth(SVNDepth.INFINITY);
					copy.setMakeParents(true);
					copy.setFailWhenDstExists(false);
					copy.setMove(isMove);
					copy.addCopySource(copySource);
					copy.setSingleTarget(target);
					_operations.add(copy);

					SVNRevision revisionBefore = SVNRevision.create(revision - 1);
					SVNRevision changeRevision = SVNRevision.create(revision);

					SVNURL origTargetUrl =
						SVNURL.parseURIDecoded(_config.getSvnURL() + origTargetPath);
					SvnTarget mergeSource = SvnTarget.fromURL(origTargetUrl, changeRevision);

					SvnMerge merge = operations().createMerge();
					merge.setAllowMixedRevisions(true);
					merge.setDepth(SVNDepth.EMPTY);
					merge.setIgnoreAncestry(true);
					merge.addRevisionRange(SvnRevisionRange.create(revisionBefore, changeRevision));
					merge.setSource(mergeSource, false);
					merge.setSingleTarget(target);
					_operations.add(merge);
				}
			}
		}

		for (SVNLogEntryPath pathEntry : _logEntry.getChangedPaths().values()) {
			if (ChangeType.fromSvn(pathEntry.getType()) == ChangeType.DELETED) {
				String targetPath = pathEntry.getPath();
				String targetBranch = getBranch(targetPath);
				String targetResource = getModulePath(targetPath, targetBranch.length());

				if (!excludePaths.contains(targetResource)) {
					final File targetFile = new File(_config.getWorkspaceRoot(), targetResource);
					SvnDelete delete = new SvnDelete(operations());
					delete.setSingleTargetFile(targetFile);
					_operations.add(delete);

					excludePaths.add(targetResource);
				}
			}
		}

		return excludePaths;
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

	private void addRecordOnly(MergeResourceBuilder builder) throws SVNException {
		addMerges(recordOnly(createMerges(builder)));
	}

	private Collection<SvnMerge> recordOnly(Collection<SvnMerge> merges) {
		for (SvnMerge merge : merges) {
			merge.setRecordOnly(true);
		}
		return merges;
	}

	private void addMerges(MergeResourceBuilder builder) throws SVNException {
		addMerges(createMerges(builder));
	}

	private void addMerges(Collection<SvnMerge> merges) {
		_operations.addAll(merges);
	}

	private static boolean isDeleted(SVNLogEntry logEntry, String path) {
		SVNLogEntryPath pathEntry = logEntry.getChangedPaths().get(path);
		if (pathEntry == null) {
			return false;
		}

		char type = pathEntry.getType();
		return type == SVNLogEntryPath.TYPE_DELETED || type == SVNLogEntryPath.TYPE_REPLACED;
	}

	private Collection<SvnMerge> createMerges(MergeResourceBuilder builder)
			throws SVNException {
		Map<String, SvnMerge> resourcesByName = new HashMap<>();
		Map<String, SVNLogEntryPath> changedPaths = _logEntry.getChangedPaths();
		for (Entry<String, SVNLogEntryPath> entry : changedPaths.entrySet()) {
			String changedPath = entry.getKey();

			int moduleStartIndex = getModuleStartIndex(changedPath);
	
			if (moduleStartIndex < 0) {
				Log.warning("Path does not match the branch pattern: " + changedPath);
				continue;
			}
	
			String resourceName = builder.getResourceName(changedPath, moduleStartIndex);
			if (resourceName == null) {
				continue;
			}
	
			if (!resourcesByName.containsKey(resourceName)) {
				String branch = getBranch(changedPath, moduleStartIndex);
				String urlPrefix = _config.getSvnURL() + branch;
	
				SvnMerge operation =
					createMerge(ChangeType.fromSvn(entry.getValue().getType()), resourceName, urlPrefix);

				resourcesByName.put(resourceName, operation);
			}
		}
	
		return resourcesByName.values();
	}

	private SvnMerge createMerge(ChangeType changeType, String resourceName, String urlPrefix) throws SVNException {
		SvnMerge merge = operations().createMerge();
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
		SvnTarget source = SvnTarget.fromURL(sourceUrl, changeType == ChangeType.DELETED ? startRevision : endRevision);
		merge.setSource(source, false);
		SvnRevisionRange range = SvnRevisionRange.create(startRevision, endRevision);
		merge.addRevisionRange(range);
		
		merge.setIgnoreAncestry(revert);
		return merge;
	}

	private String getBranch(String changedPath) {
		return getBranch(changedPath, getModuleStartIndex(changedPath));
	}

	private static String getBranch(String changedPath, int moduleStartIndex) {
		return changedPath.substring(0, moduleStartIndex);
	}

	interface MergeResourceBuilder {

		String getResourceName(String changedPath, int moduleStartIndex);

	}

	static class CompleteModuleChangeSetBuilder implements MergeResourceBuilder {

		private Set<String> _modules;

		public CompleteModuleChangeSetBuilder(Set<String> modules) {
			_modules = modules;
		}

		@Override
		public String getResourceName(String changedPath, int moduleStartIndex) {
			String moduleName = getModuleName(changedPath, moduleStartIndex);
			if (!_modules.contains(moduleName)) {
				return null;
			}
			return moduleName;
		}
	}

	static String getModuleName(String changedPath, int moduleStartIndex) {
		int moduleEndIndex = changedPath.indexOf(Utils.SVN_SERVER_PATH_SEPARATOR, moduleStartIndex);
		if (moduleEndIndex < 0) {
			moduleEndIndex = changedPath.length();
		}
		return changedPath.substring(moduleStartIndex, moduleEndIndex);
	}

	static class PartialChangeSetBuilder implements MergeResourceBuilder {

		private Set<String> _includePaths;

		public PartialChangeSetBuilder(Set<String> includePaths) {
			_includePaths = includePaths;
		}

		@Override
		public String getResourceName(String changedPath, int moduleStartIndex) {
			String modulePath = getModulePath(changedPath, moduleStartIndex);
			if (!_includePaths.contains(modulePath)) {
				// Skip path.
				return null;
			}
			return modulePath;
		}

	}

	static class ExplicitPathChangeSetBuilder implements MergeResourceBuilder {

		private Set<String> _modules;

		private Set<String> _excludePaths;

		public ExplicitPathChangeSetBuilder(Set<String> modules, Set<String> excludePaths) {
			_modules = modules;
			_excludePaths = excludePaths;
		}

		@Override
		public String getResourceName(String changedPath, int moduleStartIndex) {
			String module = getModuleName(changedPath, moduleStartIndex);
			if (!_modules.contains(module)) {
				return null;
			}

			String modulePath = getModulePath(changedPath, moduleStartIndex);
			if (_excludePaths.contains(modulePath)) {
				// Skip path.
				return null;
			}
			return modulePath;
		}

	}

	static String getModulePath(String changedPath, int moduleStartIndex) {
		return changedPath.substring(moduleStartIndex);
	}

}
