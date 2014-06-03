package com.subcherry.merge;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
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
import com.subcherry.utils.Log;
import com.subcherry.utils.Utils;


/**
 * @version $Revision$ $Author$ $Date$
 */
public class MergeHandler extends Handler {

	private final Set<String> _modules;

	private SVNLogEntry _logEntry;

	private List<SvnOperation<?>> _operations;

	private SVNClientManager _clientManager;

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
				addMerges(new ExplicitPathChangeSetBuilder(_modules, excludePaths));

				addRecordOnly(new CompleteModuleChangeSetBuilder(_modules));
			}
		} else {
			addMerges(new PartialChangeSetBuilder(includePaths));
		}
	}

	private Set<String> handleMoves() throws SVNException {
		Set<String> excludePaths = new HashSet<>();

		for (SVNLogEntryPath pathEntry : _logEntry.getChangedPaths().values()) {
			String copyPath = pathEntry.getCopyPath();
			if (copyPath != null) {
				String targetPath = pathEntry.getPath();
				
				String copyBranch = getBranch(copyPath);
				String targetBranch = getBranch(targetPath);
				
				if (copyBranch.equals(targetBranch)) {
					boolean isMove = isDeleted(copyPath);
					
					String copyResource = getModulePath(copyPath, copyBranch.length());
					String targetResource = getModulePath(targetPath, targetBranch.length());
					
					String copyModule = getModuleName(copyPath, copyBranch.length());
					String targetModule = getModuleName(targetPath, targetBranch.length());
					if (_modules.contains(targetModule) && _modules.contains(copyModule)) {
						if (isMove) {
							excludePaths.add(copyResource);
						}
						excludePaths.add(targetResource);

						File copyFile = new File(_config.getWorkspaceRoot(), copyResource);
						File targetFile = new File(_config.getWorkspaceRoot(), targetResource);

						SVNRevision revisionBefore = SVNRevision.create(_logEntry.getRevision() - 1);
						SVNRevision changeRevision = SVNRevision.create(_logEntry.getRevision());

						SvnCopySource copySource =
							SvnCopySource.create(SvnTarget.fromFile(copyFile), SVNRevision.WORKING);
						SvnTarget target = SvnTarget.fromFile(targetFile);
						SVNURL originalTargetUrl = SVNURL.parseURIDecoded(_config.getSvnURL() + targetPath);
						SvnTarget mergeSource = SvnTarget.fromURL(originalTargetUrl, changeRevision);

						SvnCopy copy = operations().createCopy();
						copy.setRevision(SVNRevision.WORKING);
						copy.setDepth(SVNDepth.INFINITY);
						copy.setMakeParents(true);
						copy.setFailWhenDstExists(false);
						copy.setMove(isMove);
						copy.addCopySource(copySource);
						copy.setSingleTarget(target);
						_operations.add(copy);

						SvnMerge merge = operations().createMerge();
						merge.setAllowMixedRevisions(true);
						merge.setDepth(SVNDepth.EMPTY);
						merge.setIgnoreAncestry(true);
						merge.addRevisionRange(SvnRevisionRange.create(revisionBefore, changeRevision));
						merge.setSource(mergeSource, false);
						merge.setSingleTarget(target);
						_operations.add(merge);
					}
				} else {
					// Cross-branch copy. Check, if there is an original changeset with a
					// branch-local copy.

					// TODO
				}
			}
		}
		return excludePaths;
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

	private boolean isDeleted(String path) {
		SVNLogEntryPath pathEntry = _logEntry.getChangedPaths().get(path);
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
		for (String changedPath : changedPaths.keySet()) {
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
	
				SvnMerge operation = createMerge(_logEntry, resourceName, urlPrefix);

				resourcesByName.put(resourceName, operation);
			}
		}
	
		return resourcesByName.values();
	}

	private SvnMerge createMerge(SVNLogEntry logEntry, String resourceName, String urlPrefix) throws SVNException {
		SvnMerge merge = operations().createMerge();
		boolean revert = _config.getRevert();
		long revision = logEntry.getRevision();
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
		SvnTarget source = SvnTarget.fromURL(sourceUrl, startRevision);
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
