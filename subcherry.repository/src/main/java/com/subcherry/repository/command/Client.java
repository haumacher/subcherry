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
package com.subcherry.repository.command;

import java.io.File;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.subcherry.repository.command.copy.CopySource;
import com.subcherry.repository.command.log.DirEntryHandler;
import com.subcherry.repository.command.log.LogEntryHandler;
import com.subcherry.repository.command.status.StatusHandler;
import com.subcherry.repository.command.wc.PropertyHandler;
import com.subcherry.repository.core.CommitInfo;
import com.subcherry.repository.core.Depth;
import com.subcherry.repository.core.DirEntry.Kind;
import com.subcherry.repository.core.MergeInfo;
import com.subcherry.repository.core.NodeProperties;
import com.subcherry.repository.core.PropertyData;
import com.subcherry.repository.core.PropertyValue;
import com.subcherry.repository.core.RepositoryException;
import com.subcherry.repository.core.RepositoryURL;
import com.subcherry.repository.core.Resolution;
import com.subcherry.repository.core.Revision;
import com.subcherry.repository.core.RevisionRange;
import com.subcherry.repository.core.Target;

public interface Client {

	OperationFactory getOperationsFactory();

	RepositoryURL createRepository(File path, String uuid, boolean enableRevisionProperties,
			boolean force) throws RepositoryException;

	CommitInfo commit(File[] paths, boolean keepLocks,
			String commitMessage, NodeProperties revisionProperties,
			String[] changelists, boolean keepChangelist, boolean force,
			Depth depth) throws RepositoryException;

	CommitInfo mkDir(RepositoryURL[] urls, String commitMessage,
			NodeProperties revisionProperties, boolean makeParents) throws RepositoryException;

	CommitInfo importResource(File path, RepositoryURL dstURL,
			String commitMessage, NodeProperties revisionProperties, boolean useGlobalIgnores,
			boolean ignoreUnknownNodeTypes,
			Depth depth) throws RepositoryException;

	void update(File[] paths, Revision revision, Depth depth,
			boolean allowUnversionedObstructions, boolean depthIsSticky) throws RepositoryException;

	void checkout(RepositoryURL url, File dstPath, Revision pegRevision,
			Revision revision, Depth depth, boolean allowUnversionedObstructions) throws RepositoryException;

	CommitInfo copy(CopySource[] sources, RepositoryURL dst,
			boolean isMove, boolean makeParents, boolean failWhenDstExists, String commitMessage,
			NodeProperties revisionProperties) throws RepositoryException;

	void copy(CopySource[] sources, File dst, boolean isMove, boolean makeParents,
			boolean failWhenDstExists) throws RepositoryException;

	void diff(Target target, Revision startRev, Revision stopRev,
			Depth depth, boolean useAncestry, OutputStream result) throws RepositoryException;

	void merge(RepositoryURL url, Revision pegRevision,
			Collection<RevisionRange> rangesToMerge, File dstPath,
			Depth depth, boolean useAncestry, boolean force, boolean dryRun, boolean recordOnly)
			throws RepositoryException;

	void log(RepositoryURL url, String[] paths, Revision pegRevision,
			Revision startRevision, Revision endRevision, boolean stopOnCopy,
			boolean discoverChangedPaths, long limit, LogEntryHandler handler) throws RepositoryException;

	void log(RepositoryURL url, String[] paths,
			Revision pegRevision, Revision startRevision,
			Revision endRevision, boolean stopOnCopy, boolean discoverChangedPaths, boolean includeMergedRevisions,
			long limit, String[] revisionProperties, LogEntryHandler handler) throws RepositoryException;

	void list(RepositoryURL url, Revision pegRevision,
			Revision revision, boolean fetchLocks, Depth depth, Kind entryFields,
			DirEntryHandler handler) throws RepositoryException;

	void status(File path, Revision revision, Depth depth,
			boolean remote, boolean reportAll, boolean includeIgnored, StatusHandler handler,
			Collection<String> changeLists) throws RepositoryException;

	void add(File path, boolean force, boolean mkdir, boolean climbUnversionedParents, Depth depth,
			boolean includeIgnored, boolean makeParents) throws RepositoryException;

	void delete(File path, boolean force, boolean deleteFiles, boolean dryRun) throws RepositoryException;

	PropertyData getProperty(File path, String propName,
			Revision pegRevision, Revision revision) throws RepositoryException;

	void setProperty(File path, String propName, PropertyValue propValue,
			boolean skipChecks, Depth depth, PropertyHandler handler, Collection<String> changeLists)
			throws RepositoryException;

	void getMergeInfoLog(Target target, Target mergeSource, Revision startRev, Revision endRev, LogEntryHandler handler)
			throws RepositoryException;

	MergeInfo getMergeInfo(Target target) throws RepositoryException;

	Map<String, List<RevisionRange>> mergeInfoDiff(Target target, long rev) throws RepositoryException;

	void resolve(File target, Depth depth, Resolution resolution) throws RepositoryException;

	void cleanup(File path) throws RepositoryException;

}
