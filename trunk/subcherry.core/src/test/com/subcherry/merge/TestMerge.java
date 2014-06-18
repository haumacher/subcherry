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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import junit.framework.TestCase;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

import test.com.subcherry.scenario.Scenario;
import test.com.subcherry.scenario.WC;

import com.subcherry.BranchConfig;
import com.subcherry.MergeConfig;
import com.subcherry.merge.Merge;
import com.subcherry.merge.MergeContext;
import com.subcherry.merge.MergeHandler;
import com.subcherry.utils.PathParser;

import de.haumacher.common.config.ValueFactory;

public class TestMerge extends TestCase {

	public void testMerge() throws IOException, SVNException {
		Scenario s = scenario();
		s.mkdir("/branches");
		s.mkdir("/branches/branch1");
		s.mkdir("/branches/branch1/module1");
		s.mkdir("/branches/branch1/module2");
		s.mkdir("/branches/branch1/module3");

		s.copy("/branches/branch2", "/branches/branch1");

		WC wc1 = s.wc("/branches/branch1");
		wc1.file("module1/foo");
		wc1.file("module2/bar");
		long revision = wc1.commit();

		WC wc2 = s.wc("/branches/branch2");

		MergeConfig mergeConfig = ValueFactory.newInstance(MergeConfig.class);
		mergeConfig.setSvnURL(s.getRepositoryUrl().toDecodedString());
		mergeConfig.setSemanticMoves(true);
		mergeConfig.setWorkspaceRoot(wc2.getDirectory());
		BranchConfig branchConfig = ValueFactory.newInstance(BranchConfig.class);
		branchConfig.setBranchPattern("/branches/[^/]+/");
		MergeHandler handler =
			new MergeHandler(s.clientManager(), mergeConfig, new PathParser(branchConfig), new HashSet<>(
				Arrays.asList(
				"module1",
				"module2")));

		SVNLogEntry entry = s.log(revision);
		assertTrue(entry.getChangedPaths().get("/branches/branch1/module1/foo") != null);
		Merge merge = handler.parseMerge(entry);
		MergeContext context = new MergeContext(s.clientManager().getDiffClient());
		merge.run(context);
		long mergedRevision = wc2.commit();

		SVNLogEntry mergedEntry = s.log(mergedRevision);
		Map<String, SVNLogEntryPath> changedPaths = mergedEntry.getChangedPaths();
		assertTrue(changedPaths.get("/branches/branch2/module1/foo") != null);
		assertTrue(changedPaths.get("/branches/branch2/module2/bar") != null);
		assertTrue(changedPaths.get("/branches/branch2/module1") != null);
		assertTrue(changedPaths.get("/branches/branch2/module2") != null);
		assertEquals("/branches/branch1/module1/foo", changedPaths.get("/branches/branch2/module1/foo").getCopyPath());
		assertEquals("/branches/branch1/module2/bar", changedPaths.get("/branches/branch2/module2/bar").getCopyPath());
	}

}
