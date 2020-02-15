/*
 * SubCherry - Cherry Picking with Trac and Subversion
 * Copyright (C) 2015 Bernhard Haumacher and others
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
package com.subcherry.repository.javahl.internal;

import static com.subcherry.repository.javahl.internal.Conversions.*;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.ConflictDescriptor;
import org.apache.subversion.javahl.ConflictResult;
import org.apache.subversion.javahl.ConflictResult.Choice;
import org.apache.subversion.javahl.ISVNClient;
import org.apache.subversion.javahl.ISVNRepos;
import org.apache.subversion.javahl.SVNClient;
import org.apache.subversion.javahl.SVNRepos;
import org.apache.subversion.javahl.SubversionException;
import org.apache.subversion.javahl.callback.CommitCallback;
import org.apache.subversion.javahl.callback.CommitMessageCallback;
import org.apache.subversion.javahl.callback.ConflictResolverCallback;
import org.apache.subversion.javahl.callback.ListCallback;
import org.apache.subversion.javahl.callback.LogMessageCallback;
import org.apache.subversion.javahl.callback.StatusCallback;
import org.apache.subversion.javahl.types.DirEntry;
import org.apache.subversion.javahl.types.Lock;
import org.apache.subversion.javahl.types.Mergeinfo.LogKind;
import org.apache.subversion.javahl.types.Status;

import com.subcherry.repository.LoginCredential;
import com.subcherry.repository.command.DefaultClient;
import com.subcherry.repository.command.OperationFactory;
import com.subcherry.repository.command.copy.CopySource;
import com.subcherry.repository.command.diff.DiffOptions;
import com.subcherry.repository.command.log.DirEntryHandler;
import com.subcherry.repository.command.log.LogEntryHandler;
import com.subcherry.repository.command.status.StatusHandler;
import com.subcherry.repository.command.status.StatusType;
import com.subcherry.repository.command.wc.PropertyHandler;
import com.subcherry.repository.core.CommitInfo;
import com.subcherry.repository.core.Depth;
import com.subcherry.repository.core.DirEntry.Kind;
import com.subcherry.repository.core.MergeInfo;
import com.subcherry.repository.core.NodeProperties;
import com.subcherry.repository.core.PropertyData;
import com.subcherry.repository.core.PropertyValue;
import com.subcherry.repository.core.RepositoryException;
import com.subcherry.repository.core.RepositoryRuntimeException;
import com.subcherry.repository.core.RepositoryURL;
import com.subcherry.repository.core.Resolution;
import com.subcherry.repository.core.Revision;
import com.subcherry.repository.core.RevisionRange;
import com.subcherry.repository.core.Target;
import com.subcherry.repository.merge.properties.PropertiesMerge;

public class HLClient extends DefaultClient {

	private static final String[] DEFAULT_REVPROPS_ARRAY = { SVN_DATE, SVN_AUTHOR, SVN_LOG };

	private static final HashSet<String> DEFAULT_REVPROPS = new HashSet<String>(Arrays.asList(DEFAULT_REVPROPS_ARRAY));

	private ISVNClient _client;

	private ISVNRepos _repos;

	private HLClientManager _clientManager;

	private Collection<HLConflictListener> _listeners = new ArrayList<HLConflictListener>();

	public HLClient(HLClientManager clientManager, LoginCredential credentials) {
		_clientManager = clientManager;
		_client = new SVNClient();
		if (credentials != null) {
			_client.username(credentials.getUser());
			_client.password(credentials.getPasswd());
		}
		ConflictResolverCallback resolver = new ConflictResolverCallback() {
			@Override
			public ConflictResult resolve(ConflictDescriptor descriptor)
					throws SubversionException {
				if (descriptor.getKind() != ConflictDescriptor.Kind.text) {
					return postPone(descriptor);
				}
				if (descriptor.getAction() != ConflictDescriptor.Action.edit) {
					return postPone(descriptor);
				}
				if (!descriptor.getPath().endsWith(".properties")) {
					return postPone(descriptor);
				}

				File baseFile = new File(descriptor.getBasePath());
				File localFile = new File(descriptor.getMyPath());
				File latestFile = new File(descriptor.getTheirPath());
				DiffOptions options = null;
				File resultFile = new File(descriptor.getMergedPath());

				StatusType result;
				try {
					result = new PropertiesMerge().merge(baseFile, localFile,
							latestFile, options, resultFile);
				} catch (RepositoryException ex) {
					throw new RepositoryRuntimeException(ex);
				}

				if (result == StatusType.MERGED) {
					return resolved(descriptor);
				} else {
					return postPone(descriptor);
				}
			}

			private ConflictResult resolved(ConflictDescriptor descriptor) {
				return new ConflictResult(Choice.chooseMerged,
						descriptor.getMergedPath());
			}

			private ConflictResult postPone(ConflictDescriptor descriptor) {
				for (HLConflictListener listener : _listeners) {
					listener.conflictDetected(descriptor);
				}

				return new ConflictResult(Choice.postpone,
						descriptor.getMergedPath());
			}
		};
		_client.setConflictResolver(resolver);

		_repos = new SVNRepos();
	}

	public ISVNClient impl() {
		return _client;
	}

	@Override
	public OperationFactory getOperationsFactory() {
		return _clientManager.getOperationsFactory();
	}

	@Override
	public void cleanup(File path) throws RepositoryException {
		try {
			_client.cleanup(path.getAbsolutePath());
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public RepositoryURL createRepository(File path, String uuid,
			boolean enableRevisionProperties, boolean force)
			throws RepositoryException {
		boolean disableFsyncCommit = true;
		boolean keepLog = false;
		File configPath = null;
		String fstype = SVNRepos.FSFS;

		try {
			_repos.create(path, disableFsyncCommit, keepLog, configPath, fstype);
		} catch (ClientException ex) {
			throw wrap(ex);
		}

		return new RepositoryURL("file", "", -1, path.getAbsolutePath().replace(File.separatorChar, '/'));
	}

	@Override
	public CommitInfo commit(File[] paths, boolean keepLocks,
			final String commitMessage, NodeProperties revisionProperties,
			String[] changelists, boolean keepChangelist, boolean force,
			Depth depth) throws RepositoryException {

		boolean noUnlock = false;
		Map<String, String> revpropTable = unwrap(revisionProperties);
		LastCommitInfo callback = new LastCommitInfo();
		try {
			_client.commit(pathSet(paths), unwrap(depth), noUnlock,
				keepChangelist, wrap(changelists), revpropTable,
				wrapMessage(commitMessage), callback);
		} catch (ClientException ex) {
			throw wrap(ex);
		}
		return callback.getInfo();
	}

	@Override
	public CommitInfo mkDir(RepositoryURL[] urls, String commitMessage,
			NodeProperties revisionProperties, boolean makeParents)
			throws RepositoryException {
		LastCommitInfo callback = new LastCommitInfo();
		try {
			_client.mkdir(pathSet(urls), makeParents,
				unwrap(revisionProperties), wrapMessage(commitMessage),
				callback);
		} catch (ClientException ex) {
			throw wrap(ex);
		}
		return callback.getInfo();
	}

	@Override
	public CommitInfo importResource(File path, RepositoryURL dstURL,
			String commitMessage, NodeProperties revisionProperties,
			boolean useGlobalIgnores, boolean ignoreUnknownNodeTypes,
			Depth depth) throws RepositoryException {
		boolean noIgnore = false;
		CommitMessageCallback handler = wrapMessage(commitMessage);
		LastCommitInfo callback = new LastCommitInfo();
		try {
			_client.doImport(unwrap(path), unwrap(dstURL), unwrap(depth),
				noIgnore, ignoreUnknownNodeTypes,
				unwrap(revisionProperties), handler, callback);
		} catch (ClientException ex) {
			throw wrap(ex);
		}
		return callback.getInfo();
	}

	@Override
	public void update(File[] paths, Revision revision, Depth depth,
			boolean allowUnversionedObstructions, boolean depthIsSticky)
			throws RepositoryException {
		boolean makeParents = false;
		boolean ignoreExternals = false;
		try {
			_client.update(pathSet(paths), unwrap(revision), unwrap(depth),
				depthIsSticky, makeParents, ignoreExternals,
				allowUnversionedObstructions);
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public void checkout(RepositoryURL url, File dstPath, Revision pegRevision,
			Revision revision, Depth depth, boolean allowUnversionedObstructions)
			throws RepositoryException {
		boolean ignoreExternals = false;
		String moduleName = unwrap(url);
		try {
			_client.checkout(moduleName, unwrap(dstPath), unwrap(revision),
				unwrap(pegRevision), unwrap(depth), ignoreExternals,
				allowUnversionedObstructions);
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public CommitInfo copy(CopySource[] sources, RepositoryURL dst,
			boolean isMove, boolean makeParents, boolean failWhenDstExists,
			String commitMessage, NodeProperties revisionProperties)
			throws RepositoryException {
		boolean copyAsChild = false;
		boolean ignoreExternals = false;
		CommitMessageCallback handler = wrapMessage(commitMessage);
		LastCommitInfo callback = new LastCommitInfo();
		try {
			_client.copy(unwrapSources(sources), unwrap(dst), copyAsChild,
				makeParents, ignoreExternals, unwrap(revisionProperties),
				handler, callback);
		} catch (ClientException ex) {
			throw wrap(ex);
		}
		return callback.getInfo();
	}

	@Override
	public void copy(CopySource[] sources, File dst, boolean isMove,
			boolean makeParents, boolean failWhenDstExists)
			throws RepositoryException {
		boolean copyAsChild = false;
		boolean ignoreExternals = false;
		try {
			_client.copy(unwrapSources(sources), unwrap(dst), copyAsChild,
				makeParents, ignoreExternals, null, null, null);
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public void diff(Target target, Revision startRev,
			Revision stopRev, Depth depth, boolean useAncestry,
			OutputStream result)
			throws RepositoryException {
		String relativeToDir = null;
		Collection<String> changelists = null;
		boolean ignoreAncestry = !useAncestry;
		boolean noDiffDeleted = false;
		boolean force = false;
		boolean copiesAsAdds = true;
		boolean ignoreProps = false;
		boolean propsOnly = false;
		try {
			_client.diff(unwrap(target), unwrap(target.getPegRevision()), unwrap(startRev),
				unwrap(stopRev), relativeToDir, result, unwrap(depth),
				changelists, ignoreAncestry, noDiffDeleted, force,
				copiesAsAdds, ignoreProps, propsOnly);
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public void merge(RepositoryURL url, Revision pegRevision,
			Collection<RevisionRange> rangesToMerge, File dstPath, Depth depth,
			boolean useAncestry, boolean force, boolean dryRun,
			boolean recordOnly) throws RepositoryException {
		boolean ignoreAncestry = !useAncestry;
		try {
			_client.merge(unwrap(url), unwrap(pegRevision),
				unwrapRanges(rangesToMerge), unwrap(dstPath), force,
				unwrap(depth), ignoreAncestry, dryRun, recordOnly);
		} catch (RepositoryRuntimeException ex) {
			throw ex.getCause();
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public void log(RepositoryURL url, String[] paths, Revision pegRevision,
			Revision startRevision, Revision endRevision, boolean stopOnCopy,
			boolean discoverChangedPaths, long limit,
			final LogEntryHandler handler) throws RepositoryException {
		log(url, paths, pegRevision, startRevision, endRevision, stopOnCopy, discoverChangedPaths, false, limit,
			DEFAULT_REVPROPS_ARRAY, handler);
	}

	@Override
	public void log(RepositoryURL url, String[] paths, Revision pegRevision,
			Revision startRevision, Revision endRevision, boolean stopOnCopy,
			boolean discoverChangedPaths, boolean includeMergedRevisions,
			long limit, String[] revisionProperties, final LogEntryHandler handler)
			throws RepositoryException {
		final LogFilter logFilter = new LogFilter(unwrap(url), paths);
		LogMessageCallback callback = wrap(handler, logFilter);
		try {
			_client.logMessages(logFilter.getPrefixUrl(), unwrap(pegRevision),
				range(startRevision, endRevision), stopOnCopy,
				discoverChangedPaths, includeMergedRevisions, properties(revisionProperties),
				limit, callback);
		} catch (RepositoryRuntimeException ex) {
			throw ex.getCause();
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	private static Set<String> properties(String[] revisionProperties) {
		if (revisionProperties == null) {
			return DEFAULT_REVPROPS;
		}
		return set(revisionProperties);
	}

	@Override
	public void list(RepositoryURL url, Revision pegRevision, Revision revision, boolean fetchLocks, Depth depth,
			Kind entryFields, final DirEntryHandler handler) throws RepositoryException {
		ListCallback callback = new ListCallback() {
			@Override
			public void doEntry(DirEntry dirent, Lock lock) {
				try {
					handler.handleDirEntry(wrap(dirent));
				} catch (RepositoryException ex) {
					throw new RepositoryRuntimeException(ex);
				}
			}
		};
		try {
			_client.list(unwrap(url), unwrap(revision), unwrap(pegRevision), unwrap(depth), unwrap(entryFields),
				fetchLocks, callback);
		} catch (RepositoryRuntimeException ex) {
			throw ex.getCause();
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public void status(File path, Revision revision, Depth depth,
			boolean remote, boolean reportAll, boolean includeIgnored,
			final StatusHandler handler, Collection<String> changeLists) throws RepositoryException {
		boolean noIgnore = includeIgnored;
		boolean ignoreExternals = false;
		StatusCallback callback = new StatusCallback() {
			@Override
			public void doStatus(String statusPath, Status status) {
				try {
					handler.handleStatus(wrap(status));
				} catch (RepositoryException ex) {
					throw new RepositoryRuntimeException(ex);
				}
			}
		};
		try {
			_client.status(unwrap(path), unwrap(depth), remote, reportAll, noIgnore, ignoreExternals, changeLists,
				callback);
		} catch (RepositoryRuntimeException ex) {
			throw ex.getCause();
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public void add(File path, boolean force, boolean mkdir,
			boolean climbUnversionedParents, Depth depth,
			boolean includeIgnored, boolean makeParents)
			throws RepositoryException {
		try {
			_client.add(unwrap(path), unwrap(depth), force, includeIgnored, climbUnversionedParents);
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public void delete(File path, boolean force, boolean deleteFiles,
			boolean dryRun) throws RepositoryException {
		boolean keepLocal = !deleteFiles;
		Map<String, String> revpropTable = null;
		CommitMessageCallback handler = null;
		CommitCallback callback = null;
		try {
			_client.remove(Collections.singleton(unwrap(path)), force, keepLocal, revpropTable, handler, callback);
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public PropertyData getProperty(File path, String propName,
			Revision pegRevision, Revision revision) throws RepositoryException {
		try {
			return wrap(propName, _client.propertyGet(unwrap(path), propName, unwrap(revision), unwrap(pegRevision)));
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public void setProperty(File path, String propName,
			PropertyValue propValue, boolean skipChecks, Depth depth,
			PropertyHandler handler, Collection<String> changeLists)
			throws RepositoryException {
		boolean force = false;
		try {
			_client.propertySetLocal(Collections.singleton(unwrap(path)), propName, unwrap(propValue), unwrap(depth),
				changeLists, force);
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public MergeInfo getMergeInfo(Target target) throws RepositoryException {
		try {
			return wrap(_client.getMergeinfo(unwrap(target), unwrap(target.getPegRevision())));
		} catch (SubversionException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public void getMergeInfoLog(Target target, Target mergeSource,
			Revision startRev, Revision endRev, final LogEntryHandler handler) throws RepositoryException {
		LogKind kind = LogKind.merged;
		String pathOrUrl = unwrap(target);
		org.apache.subversion.javahl.types.Revision pegRevision = unwrap(target.getPegRevision());
		String mergeSourceUrl = unwrap(mergeSource);
		org.apache.subversion.javahl.types.Revision srcPegRevision = unwrap(mergeSource.getPegRevision());
		org.apache.subversion.javahl.types.Revision srcStartRevision = unwrap(startRev);
		org.apache.subversion.javahl.types.Revision srcEndRevision = unwrap(endRev);
		boolean discoverChangedPaths = false;
		org.apache.subversion.javahl.types.Depth depth = org.apache.subversion.javahl.types.Depth.empty;
		Set<String> revProps = Collections.emptySet();
		try {
			_client.getMergeinfoLog(kind, pathOrUrl, pegRevision,
					mergeSourceUrl, srcPegRevision, srcStartRevision,
					srcEndRevision, discoverChangedPaths, depth, revProps,
					wrap(handler));
		} catch (RepositoryRuntimeException ex) {
			throw ex.getCause();
		} catch (ClientException ex) {
			throw wrap(ex);
		}
	}

	@Override
	public void resolve(File target, Depth depth, Resolution resolution)
			throws RepositoryException {
		if (!target.exists()) {
			// JavaHL behaves strange on reported tree conflicts. The conflict
			// cannot be resolved, and committing the whole working copy works
			// just fine.
			return;
		}
		try {
			_client.resolve(unwrap(target), unwrap(depth), unwrap(resolution));
		} catch (SubversionException ex) {
			throw wrap(ex);
		}
	}

	public void addConflictListener(HLConflictListener listener) {
		_listeners.add(listener);
	}

	public void removeConflictListener(HLConflictListener listener) {
		_listeners.remove(listener);
	}

}
