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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

import com.subcherry.history.Change;
import com.subcherry.history.HistroyBuilder;
import com.subcherry.history.Node;

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

		_builder = new HistroyBuilder();
	}

	@Override
	protected void tearDown() throws Exception {
		_builder = null;

		super.tearDown();
	}

	public void testBranchRename() throws SVNException {
		modify(100, "/trunk/module/file-1");
		move(90, "/branches/stable", "/trunk");
		modify(80, "/branches/stable/module/file-1");
		move(70, "/branches/unstable", "/branches/stable");
		modify(60, "/branches/unstable/module/file-1");
		create(50, "/branches/unstable/module/file-1");

		assertChanges(revisions(100, 80, 60, 50), "/trunk/module/file-1");
		assertChanges(revisions(90, 70), "/trunk");

		assertNoChanges("/branches/stable");
		assertNoChanges("/branches/stable/module/file-1");
		assertNoChanges("/branches/unstable");
		assertNoChanges("/branches/unstable/module/file-1");
	}

	public void testReplace() throws SVNException {
		replace(100, "/b2", "/b1");

		modify(90, "/b1/file");
		modify(80, "/b2/file");

		create(70, "/b2/file");
		create(60, "/b1/file");

		assertChanges(revisions(80, 70), "/b1/file");
		assertNoChanges("/b2/file");
	}

	public void testSwapBranches() throws SVNException {
		modify(200, "/b2/file");
		modify(190, "/b1/file");

		move(180, "/tmp", "/b1");
		move(170, "/b1", "/b2");
		move(160, "/b2", "/tmp");

		modify(150, "/b1/file");
		modify(140, "/b2/file");

		move(130, "/tmp", "/b2");
		move(120, "/b2", "/b1");
		move(110, "/b1", "/tmp");

		create(100, "/b2/file");
		create(90, "/b1/file");

		assertChanges(revisions(190, 140, 90), "/b1/file");
		assertChanges(revisions(200, 150, 100), "/b2/file");
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

	private void move(long revision, String fromPath, String toPath) throws SVNException {
		Map<String, SVNLogEntryPath> paths = paths(copied(fromPath, toPath, revision - 1), deleted(fromPath));
		apply(revision, paths);
	}

	private void replace(long revision, String fromPath, String toPath) throws SVNException {
		Map<String, SVNLogEntryPath> paths = paths(replaced(fromPath, toPath, revision - 1), deleted(fromPath));
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
		Node node = _builder.getHistory().getNode(path);
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
		HashMap<String, SVNLogEntryPath> result = new HashMap<>();
		for (SVNLogEntryPath pathEntry : pathEntries) {
			SVNLogEntryPath clash = result.put(pathEntry.getPath(), pathEntry);
			assertNull(clash);
		}
		return result;
	}
}
