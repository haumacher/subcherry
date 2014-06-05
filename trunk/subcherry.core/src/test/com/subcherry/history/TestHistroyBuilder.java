/*
 * TimeCollect records time you spent on your development work.
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
package test.com.subcherry.history;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

import com.subcherry.history.Change;
import com.subcherry.history.HistroyBuilder;
import com.subcherry.history.Node;
import com.subcherry.history.Node.Kind;

/**
 * Test case for {@link HistroyBuilder}.
 * 
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 * @version $Revision$ $Author$ $Date$
 */
@SuppressWarnings("javadoc")
public class TestHistroyBuilder extends TestCase {
	private static final Date DATE = new Date();

	private HistroyBuilder _builder;

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		_builder = new HistroyBuilder(10);
	}

	@Override
	protected void tearDown() throws Exception {
		_builder = null;

		super.tearDown();
	}

	public void testDeleteParent() throws SVNException {
		create(50, "/branches/unstable/module/file-1");
		modify(60, "/branches/unstable/module/file-1");

		assertChanges(revisions(50, 60), "/branches/unstable/module/file-1");

		delete(70, "/branches/unstable");

		assertNoChanges("/branches/unstable");
		assertNoChanges("/branches/unstable/module/file-1");
	}

	public void testBranchRename() throws SVNException {
		create(40, "/branches/unstable");

		create(50, "/branches/unstable/module/file-1");
		modify(60, "/branches/unstable/module/file-1");

		assertChanges(revisions(50, 60), "/branches/unstable/module/file-1");

		move(70, "/branches/unstable", "/branches/stable");

		assertNoChanges("/branches/unstable");
		assertNoChanges("/branches/unstable/module/file-1");

		modify(80, "/branches/stable/module/file-1");

		assertChanges(revisions(50, 60, 80), "/branches/stable/module/file-1");

		move(90, "/branches/stable", "/trunk");
		modify(100, "/trunk/module/file-1");

		assertChanges(revisions(50, 60, 80, 100), "/trunk/module/file-1");
		assertChanges(revisions(40, 70, 90), "/trunk");

		assertNoChanges("/branches/stable");
		assertNoChanges("/branches/stable/module/file-1");
		assertNoChanges("/branches/unstable");
		assertNoChanges("/branches/unstable/module/file-1");
	}

	public void testLookupDeletedBranchedDir() throws SVNException {
		create(50, "/branch");
		create(60, "/branch/module");
		create(70, "/branch/module/file");
		modify(80, "/branch/module/file");

		copy(90, "/branch", "/trunk");
		delete(100, "/trunk/module");

		assertNoChanges("/trunk/module");
	}

	public void testImplicitBranchCreation() throws SVNException {
		// The creation of "/branches/unstable" is not recorded.
	
		create(50, "/branches/unstable/module/file-1");
		modify(60, "/branches/unstable/module/file-1");
	
		assertChanges(revisions(50, 60), "/branches/unstable/module/file-1");
	
		move(70, "/branches/unstable", "/branches/stable");
		modify(80, "/branches/stable/module/file-1");
	
		assertChanges(revisions(50, 60, 80), "/branches/stable/module/file-1");
	}

	public void testMultipleCopiesFromImplicitBranch() throws SVNException {
		// The creation of "/branches/unstable" is not recorded.

		create(50, "/branches/unstable/module/file-1");
		modify(60, "/branches/unstable/module/file-1");

		assertChanges(revisions(50, 60), "/branches/unstable/module/file-1");

		copy(70, "/branches/unstable", "/branches/rc1");
		modify(80, "/branches/rc1/module/file-1");

		copy(90, "/branches/unstable", "/branches/rc2");
		modify(100, "/branches/rc2/module/file-1");

		assertChanges(revisions(50, 60, 100), "/branches/rc2/module/file-1");
	}

	public void testCopyFromBeforeImplicitCreation() throws SVNException {
		// The creation of "/branches/unstable" is not recorded.

		modify(60, "/branches/unstable/file");
		copy(70, "/branches/unstable", 50, "/branches/stable");

		modify(80, "/branches/stable/file");

		assertChanges(revisions(60), "/branches/unstable/file");
		assertChanges(revisions(80), "/branches/stable/file");
	}

	public void testReplace() throws SVNException {
		create(60, "/b1/file");
		create(70, "/b2/file");

		modify(80, "/b2/file");
		modify(90, "/b1/file");

		replace(100, "/b2", "/b1");

		assertChanges(revisions(70, 80), "/b1/file");
		assertNoChanges("/b2/file");
	}

	public void testSwapBranches() throws SVNException {
		create(70, "/b1");
		create(80, "/b2");

		create(90, "/b1/file");
		create(100, "/b2/file");

		move(110, "/b1", "/tmp");
		move(120, "/b2", "/b1");
		move(130, "/tmp", "/b2");

		modify(140, "/b2/file");
		modify(150, "/b1/file");

		move(160, "/b2", "/tmp");
		move(170, "/b1", "/b2");
		move(180, "/tmp", "/b1");

		modify(190, "/b1/file");
		modify(200, "/b2/file");

		assertChanges(revisions(90, 140, 190), "/b1/file");
		assertChanges(revisions(100, 150, 200), "/b2/file");
	}

	public void testCurrentViewOfCopiedChanges() throws SVNException {
		create(50, "/branches/unstable");
		create(60, "/branches/unstable/file-1");
		modify(70, "/branches/unstable/file-1");

		copy(80, "/branches/unstable", "/branches/stable");
		create(90, "/branches/stable/file-2");
		modify(100, "/branches/stable/file-2");

		expandContents("/branches/stable");
		assertNodes(
			"/branches/stable",
			"/branches/stable/file-1",
			"/branches/stable/file-2");
	}

	public void testCurrentViewOfCopiedChangesBeforeDeletion() throws SVNException {
		create(50, "/branches/unstable");
		create(60, "/branches/unstable/file-1");
		modify(70, "/branches/unstable/file-1");
		create(80, "/branches/unstable/file-2");
		modify(90, "/branches/unstable/file-3");
		delete(100, "/branches/unstable");

		copy(110, "/branches/unstable", 70, "/branches/stable");
		create(120, "/branches/stable/file-3");
		modify(130, "/branches/stable/file-3");

		expandContents("/branches/stable");
		assertNodes(
			"/branches/stable",
			"/branches/stable/file-1",
			"/branches/stable/file-3");
	}

	private void assertNodes(String... expectedPaths) {
		List<Node> nodes = _builder.getHistory().getNodes(expectedPaths[0]);
		List<String> paths = new ArrayList<>();
		for (Node node : nodes) {
			paths.add(node.getPath());
		}
		assertEquals(Arrays.asList(expectedPaths), paths);
	}

	private void expandContents(String path) {
		_builder.getHistory().expandContents(path);
	}

	private void create(long revision, String path) throws SVNException {
		Map<String, SVNLogEntryPath> paths = paths(added(path));
		apply(revision, paths);
	}

	private void modify(long revision, String path) throws SVNException {
		Map<String, SVNLogEntryPath> paths = paths(modified(path));
		apply(revision, paths);
	}

	private void delete(long revision, String path) throws SVNException {
		Map<String, SVNLogEntryPath> paths = paths(deleted(path));
		apply(revision, paths);
	}

	private void copy(long revision, String fromPath, String toPath) throws SVNException {
		copy(revision, fromPath, revision - 1, toPath);
	}

	private void copy(long revision, String fromPath, long fromRevision, String toPath) throws SVNException {
		Map<String, SVNLogEntryPath> paths = paths(copied(fromPath, toPath, fromRevision));
		apply(revision, paths);
	}

	private void move(long revision, String fromPath, String toPath) throws SVNException {
		Map<String, SVNLogEntryPath> paths = paths(copied(fromPath, toPath, revision - 1), deleted(fromPath));
		apply(revision, paths);
	}

	private void replace(long revision, String fromPath, String toPath) throws SVNException {
		Map<String, SVNLogEntryPath> paths = paths(deleted(fromPath), replaced(fromPath, toPath, revision - 1));
		apply(revision, paths);
	}

	private void apply(long revision, Map<String, SVNLogEntryPath> paths) throws SVNException {
		SVNLogEntry logEntry = new SVNLogEntry(paths, revision, "", DATE, "message-" + revision);
		_builder.handleLogEntry(logEntry);
	}

	private void assertChanges(List<Long> revisions, String path) {
		assertChanges(revisions, getNodeNotNull(path));
	}

	private Node getNodeNotNull(String path) {
		Node node = getNode(path);
		assertNotNull("Path not found: " + path, node);
		return node;
	}

	private Node getNode(String path) {
		Node node = _builder.getHistory().getCurrentNode(Kind.UNKNOWN, path);
		return node;
	}

	private void assertChanges(List<Long> revisions, Node node) {
		assertEquals(revisions, revisions(node.getChanges()));
	}

	private void assertNoChanges(String path) {
		assertNull(getNode(path));
	}

	private static SVNLogEntryPath added(String path) {
		return new SVNLogEntryPath(path, SVNLogEntryPath.TYPE_ADDED, null, 0);
	}

	private static SVNLogEntryPath copied(String fromPath, String toPath, long copyRevision) {
		return new SVNLogEntryPath(toPath, SVNLogEntryPath.TYPE_ADDED, fromPath, copyRevision);
	}

	private static SVNLogEntryPath replaced(String fromPath, String toPath, long copyRevision) {
		return new SVNLogEntryPath(toPath, SVNLogEntryPath.TYPE_REPLACED, fromPath, copyRevision);
	}

	private static SVNLogEntryPath modified(String path) {
		return new SVNLogEntryPath(path, SVNLogEntryPath.TYPE_MODIFIED, null, 0);
	}

	private static SVNLogEntryPath deleted(String fromPath) {
		return new SVNLogEntryPath(fromPath, SVNLogEntryPath.TYPE_DELETED, null, 0);
	}

	private static List<Long> revisions(long... revisions) {
		ArrayList<Long> result = new ArrayList<>();
		for (long revision : revisions) {
			result.add(revision);
		}
		return result;
	}

	private static List<Long> revisions(List<Change> changes) {
		ArrayList<Long> result = new ArrayList<>(changes.size());
		for (Change change : changes) {
			result.add(change.getRevision());
		}
		return result;
	}

	private static Map<String, SVNLogEntryPath> paths(SVNLogEntryPath pathEntry) {
		return Collections.singletonMap(pathEntry.getPath(), pathEntry);
	}

	private static Map<String, SVNLogEntryPath> paths(SVNLogEntryPath... pathEntries) {
		HashMap<String, SVNLogEntryPath> result = new LinkedHashMap<>();
		for (SVNLogEntryPath pathEntry : pathEntries) {
			SVNLogEntryPath clash = result.put(pathEntry.getPath(), pathEntry);
			assertNull(clash);
		}
		return result;
	}
}
