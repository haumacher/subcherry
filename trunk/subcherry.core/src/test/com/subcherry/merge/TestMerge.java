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
package test.com.subcherry.merge;

import static test.com.subcherry.scenario.Scenario.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;

import test.com.subcherry.scenario.Scenario;
import test.com.subcherry.scenario.WC;

import com.subcherry.BranchConfig;
import com.subcherry.CommitConfig;
import com.subcherry.MergeConfig;
import com.subcherry.commit.Commit;
import com.subcherry.commit.CommitContext;
import com.subcherry.merge.Merge;
import com.subcherry.merge.MergeContext;
import com.subcherry.merge.MergeHandler;
import com.subcherry.utils.PathParser;

import de.haumacher.common.config.ValueFactory;

/**
 * Test case for {@link MergeHandler}.
 * 
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 * @version $Revision$ $Author$ $Date$
 */
@SuppressWarnings("javadoc")
public class TestMerge extends TestCase {

	private static final List<String> MERGED_MODULES = Arrays.asList("module1", "module2");

	public void testRegularMerge() throws IOException, SVNException {
		Scenario s = moduleScenario();

		// Create scenario base.
		WC wc1 = s.wc("/branches/branch1");
		wc1.file("module1/foo");
		wc1.file("module1/bar");
		wc1.commit();

		// Create target branch.
		s.copy("/branches/branch2", "/branches/branch1");

		// Create revision to merge.
		wc1.update("module1/foo");
		wc1.delete("module1/bar");
		wc1.file("module2/baz");
		long origRevision = wc1.commit();

		SVNLogEntry mergedEntry = doMerge(s, origRevision);

		Map<String, SVNLogEntryPath> changedPaths = mergedEntry.getChangedPaths();
		// Merge info has been recorded.
		assertTrue(changedPaths.get("/branches/branch2/module1") != null);
		assertTrue(changedPaths.get("/branches/branch2/module2") != null);

		// Changes have been applied.
		assertType(mergedEntry, SVNLogEntryPath.TYPE_MODIFIED, "/branches/branch2/module1/foo");
		assertType(mergedEntry, SVNLogEntryPath.TYPE_DELETED, "/branches/branch2/module1/bar");
		assertType(mergedEntry, SVNLogEntryPath.TYPE_ADDED, "/branches/branch2/module2/baz");

		assertNull(changedPaths.get("/branches/branch2/module1/foo").getCopyPath());
		assertNull(changedPaths.get("/branches/branch2/module1/bar").getCopyPath());
		assertCopyFrom(mergedEntry, "/branches/branch1/module2/baz", "/branches/branch2/module2/baz");
	}

	public void testMoveMerge() throws IOException, SVNException {
		Scenario s = moduleScenario();

		// Create scenario base.
		WC wc1 = s.wc("/branches/branch1");
		wc1.file("module1/foo");
		wc1.file("module2/bar");
		wc1.commit();

		// Create target branch.
		s.copy("/branches/branch2", "/branches/branch1");

		// Create revision to merge, move foo to module2 and bar to module1.
		wc1.copy("module2/foo", "module1/foo");
		wc1.delete("module1/foo");
		wc1.copy("module1/bar", "module2/bar");
		wc1.delete("module2/bar");
		long origRevision = wc1.commit();

		SVNLogEntry mergedEntry = doMerge(s, origRevision);

		Map<String, SVNLogEntryPath> changedPaths = mergedEntry.getChangedPaths();
		// Merge info has been recorded.
		assertTrue(changedPaths.get("/branches/branch2/module1") != null);
		assertTrue(changedPaths.get("/branches/branch2/module2") != null);

		// Changes have been applied.
		assertType(mergedEntry, SVNLogEntryPath.TYPE_DELETED, "/branches/branch2/module1/foo");
		assertType(mergedEntry, SVNLogEntryPath.TYPE_DELETED, "/branches/branch2/module2/bar");
		assertType(mergedEntry, SVNLogEntryPath.TYPE_ADDED, "/branches/branch2/module2/foo");
		assertType(mergedEntry, SVNLogEntryPath.TYPE_ADDED, "/branches/branch2/module1/bar");

		// Intra-branch copy has been created.
		assertCopyFrom(mergedEntry, "/branches/branch2/module1/foo", "/branches/branch2/module2/foo");
		assertCopyFrom(mergedEntry, "/branches/branch2/module2/bar", "/branches/branch2/module1/bar");
	}

	/**
	 * The source of a move is replaced in the same revision.
	 */
	public void testParallelMove() throws IOException, SVNException {
		Scenario s = moduleScenario();

		// Create scenario base.
		WC wc1 = s.wc("/branches/branch1");
		wc1.file("module1/foo");
		wc1.file("module1/bar");
		wc1.commit();

		// Create target branch.
		s.copy("/branches/branch2", "/branches/branch1");

		// Create revision to merge, move foo to module2 and bar to module1.
		wc1.copy("module1/newfoo", "module1/foo");
		wc1.delete("module1/foo");
		wc1.copy("module1/foo", "module1/bar");
		wc1.delete("module1/bar");
		long origRevision = wc1.commit();

		SVNLogEntry mergedEntry = doMerge(s, origRevision);

		// Changes have been applied.
		assertType(mergedEntry, SVNLogEntryPath.TYPE_ADDED, "/branches/branch2/module1/newfoo");
		assertType(mergedEntry, SVNLogEntryPath.TYPE_REPLACED, "/branches/branch2/module1/foo");
		assertType(mergedEntry, SVNLogEntryPath.TYPE_DELETED, "/branches/branch2/module1/bar");

		// Intra-branch copy has been created.
		assertCopyFrom(mergedEntry, "/branches/branch2/module1/foo", "/branches/branch2/module1/newfoo");
		assertCopyFrom(mergedEntry, "/branches/branch2/module1/bar", "/branches/branch2/module1/foo");
	}

	public void testMoveOutOfDeletedFolder() throws IOException, SVNException {
		Scenario s = moduleScenario();

		// Create scenario base.
		WC wc1 = s.wc("/branches/branch1");
		wc1.mkdir("module1/folder");
		wc1.file("module1/folder/foo");
		wc1.commit();

		// Create target branch.
		s.copy("/branches/branch-intermediate", "/branches/branch1");
		s.copy("/branches/branch2", "/branches/branch1");

		// Create revision to merge, move foo to module2 and bar to module1.
		wc1.copy("module1/foo", "module1/folder/foo");
		wc1.delete("module1/folder");
		long origRevision = wc1.commit();

		long rebasedRevision = rebaseSvn(s, origRevision);

		SVNLogEntry mergedEntry = doMerge(s, rebasedRevision);

		// Changes have been applied.
		assertType(mergedEntry, SVNLogEntryPath.TYPE_DELETED, "/branches/branch2/module1/folder");
		assertType(mergedEntry, SVNLogEntryPath.TYPE_ADDED, "/branches/branch2/module1/foo");

		// Intra-branch copy has been created.
		assertCopyFrom(mergedEntry, "/branches/branch2/module1/folder/foo", "/branches/branch2/module1/foo");
	}

	public void testRegularlyRebasedContentChangeInMovedFolder() throws IOException, SVNException {
		Scenario s = moduleScenario();

		// Create scenario base.
		WC wc1 = s.wc("/branches/branch1");
		wc1.mkdir("module1/folder");
		wc1.file("module1/folder/foo");
		wc1.commit();

		// Create target branch.
		s.copy("/branches/branch-intermediate", "/branches/branch1");
		s.copy("/branches/branch2", "/branches/branch1");

		// Create revision to merge, move foo to module2 and bar to module1.
		wc1.copy("module1/newfolder", "module1/folder");
		wc1.delete("module1/folder");
		wc1.update("module1/newfolder/foo");
		long origRevision = wc1.commit();

		long rebasedRevision = rebaseSvn(s, origRevision);

		SVNLogEntry mergedEntry = doMerge(s, rebasedRevision);

		// Changes have been applied.
		assertType(mergedEntry, SVNLogEntryPath.TYPE_ADDED, "/branches/branch2/module1/newfolder");
		assertType(mergedEntry, SVNLogEntryPath.TYPE_DELETED, "/branches/branch2/module1/folder");
		assertType(mergedEntry, SVNLogEntryPath.TYPE_MODIFIED, "/branches/branch2/module1/newfolder/foo");

		// Intra-branch copy has been created.
		assertCopyFrom(mergedEntry, "/branches/branch2/module1/folder", "/branches/branch2/module1/newfolder");
	}

	public void testRegularRebasedFileAddToFolderWithPropertyModification() throws IOException, SVNException {
		Scenario s = moduleScenario();

		// Create scenario base.
		WC wc1 = s.wc("/branches/branch1");
		wc1.mkdir("module1/folder");
		wc1.commit();

		// Create target branch.
		s.copy("/branches/branch-intermediate", "/branches/branch1");
		s.copy("/branches/branch2", "/branches/branch1");

		// Create revision to merge, move foo to module2 and bar to module1.
		wc1.setProperty("module1/folder", "svn:ignore", "tmp");
		wc1.file("module1/folder/foo");
		long origRevision = wc1.commit();

		long rebasedRevision = rebaseSvn(s, origRevision);

		SVNLogEntry mergedEntry = doMerge(s, rebasedRevision);

		// Changes have been applied.
		assertType(mergedEntry, SVNLogEntryPath.TYPE_MODIFIED, "/branches/branch2/module1/folder");
		assertType(mergedEntry, SVNLogEntryPath.TYPE_ADDED, "/branches/branch2/module1/folder/foo");

		// Direct source branch copy has been created.
		assertCopyFrom(mergedEntry, "/branches/branch1/module1/folder/foo", "/branches/branch2/module1/folder/foo");
	}

	public void testFixRegularRebasedMove() throws IOException, SVNException {
		Scenario s = moduleScenario();

		// Create scenario base.
		WC wc1 = s.wc("/branches/branch1");
		wc1.file("module1/foo");
		wc1.commit();

		// Create intermediate branch and target branch.
		s.copy("/branches/branch-intermediate", "/branches/branch1");
		s.copy("/branches/branch2", "/branches/branch1");

		// Create original move.
		wc1.copy("module1/bar", "module1/foo");
		wc1.delete("module1/foo");
		long origRevision = wc1.commit();

		long rebasedRevision = rebaseSvn(s, origRevision);

		// Fix the intermediate rebase by applying a semantic move merge.
		SVNLogEntry mergedEntry = doMerge(s, rebasedRevision);

		// Changes have been applied.
		assertType(mergedEntry, SVNLogEntryPath.TYPE_ADDED, "/branches/branch2/module1/bar");
		assertType(mergedEntry, SVNLogEntryPath.TYPE_DELETED, "/branches/branch2/module1/foo");

		// Intra-branch copy has been created.
		assertCopyFrom(mergedEntry, "/branches/branch2/module1/foo", "/branches/branch2/module1/bar");
	}

	public void testShortCircuitRegularRebasedCreate() throws IOException, SVNException {
		Scenario s = moduleScenario();

		// Create intermediate branch and target branch.
		s.copy("/branches/branch-intermediate", "/branches/branch1");
		s.copy("/branches/branch2", "/branches/branch1");

		// Create original create.
		WC wc1 = s.wc("/branches/branch1");
		wc1.file("module1/foo");
		long origRevision = wc1.commit();

		long rebasedRevision = rebaseSvn(s, origRevision);

		// Fix the intermediate rebase by applying a semantic move merge.
		SVNLogEntry mergedEntry = doMerge(s, rebasedRevision);

		Map<String, SVNLogEntryPath> changedPaths = mergedEntry.getChangedPaths();

		// Changes have been applied.
		assertTrue(changedPaths.get("/branches/branch2/module1/foo") != null);

		// Direct copy from original branch has been created.
		assertCopyFrom(mergedEntry, "/branches/branch1/module1/foo", "/branches/branch2/module1/foo");
	}

	private long rebaseSvn(Scenario s, long origRevision) throws IOException, SVNException {
		// Create rebase of the move with a regular SVN merge creating a cross branch copy.
		WC wc2 = s.wc("/branches/branch-intermediate");
		long rebasedRevision = wc2.merge("branches/branch1", origRevision, MERGED_MODULES);
		return rebasedRevision;
	}

	private SVNLogEntry doMerge(Scenario s, long revision) throws IOException, SVNException {
		WC wc2 = s.wc("/branches/branch2");

		MergeConfig mergeConfig = ValueFactory.newInstance(MergeConfig.class);
		mergeConfig.setSvnURL(s.getRepositoryUrl().toDecodedString());
		mergeConfig.setSemanticMoves(true);
		mergeConfig.setWorkspaceRoot(wc2.getDirectory());
		BranchConfig branchConfig = ValueFactory.newInstance(BranchConfig.class);
		branchConfig.setBranchPattern("/branches/[^/]+/");
		MergeHandler handler =
			new MergeHandler(s.clientManager(), mergeConfig, new PathParser(branchConfig), new HashSet<>(
				MERGED_MODULES));

		SVNLogEntry entry = s.log(revision);
		Merge merge = handler.parseMerge(entry);
		MergeContext context = new MergeContext(s.clientManager().getDiffClient());
		Map<File, List<SVNConflictDescription>> conflicts = merge.run(context);
		assertEquals(Collections.emptyMap(), conflicts);

		CommitConfig commitConfig = ValueFactory.newInstance(CommitConfig.class);
		commitConfig.setWorkspaceRoot(wc2.getDirectory());
		Commit commit = new Commit(commitConfig, entry, null);
		commit.setCommitMessage(s.createMessage());
		commit.addTouchedResources(merge.getTouchedResources());
		SVNCommitInfo info =
			commit.run(new CommitContext(s.clientManager().getUpdateClient(), s.clientManager().getCommitClient()));

		assertEquals("Not all merged resources were committed.", Collections.emptyList(), wc2.getModified());

		SVNLogEntry mergedEntry = s.log(info.getNewRevision());
		return mergedEntry;
	}

	private Scenario moduleScenario() throws IOException, SVNException {
		Scenario s = scenario();
		s.mkdir("/branches");
		s.mkdir("/branches/branch1");
		s.mkdir("/branches/branch1/module1");
		s.mkdir("/branches/branch1/module2");
		s.mkdir("/branches/branch1/module3");
		return s;
	}

	private static void assertType(SVNLogEntry changedPaths, char expectedType, String path) {
		SVNLogEntryPath entry = changedPaths.getChangedPaths().get(path);
		assertNotNull(entry);
		assertEquals(expectedType, entry.getType());
	}

	private static void assertCopyFrom(SVNLogEntry mergedEntry, String expectedCopyPath, String path) {
		SVNLogEntryPath entry = mergedEntry.getChangedPaths().get(path);
		assertNotNull(entry);
		assertEquals(expectedCopyPath, entry.getCopyPath());
	}

}
