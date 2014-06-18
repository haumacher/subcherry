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
package test.com.subcherry.scenario;

import java.io.File;
import java.io.IOException;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class WC extends FileSystem {

	private Scenario _scenario;

	private String _path;

	private SVNURL _baseUrl;

	private File _wcPath;

	WC(Scenario scenario, String path) throws IOException, SVNException {
		_scenario = scenario;
		_path = path;

		_wcPath = File.createTempFile("wc-" + scenario.getRepositoryRoot().getName(), "");
		_wcPath.delete();
		_wcPath.mkdir();

		_baseUrl = scenario().getRepositoryUrl().appendPath(path, true);
		scenario.clientManager().getUpdateClient().doCheckout(_baseUrl, _wcPath, SVNRevision.HEAD, SVNRevision.HEAD,
			SVNDepth.INFINITY, false);
	}

	public long commit() throws SVNException {
		File[] paths = { _wcPath };
		SVNCommitInfo commitInfo = clientManager().getCommitClient()
			.doCommit(paths, false, scenario().createMessage(), null, null, false, false, SVNDepth.INFINITY);
		return commitInfo.getNewRevision();
	}

	public File getDirectory() {
		return _wcPath;
	}

	@Override
	public void mkdir(String path) throws SVNException {
		File dir = toFile(path);
		dir.mkdirs();
		add(dir);
	}

	@Override
	public void file(String path) throws SVNException, IOException {
		File file = toFile(path);
		scenario().fillFileContent(file);
		add(file);
	}

	@Override
	public void copy(String toPath, String fromPath) throws SVNException {
		SVNCopySource[] sources = { new SVNCopySource(SVNRevision.BASE, SVNRevision.BASE, toFile(fromPath)) };
		File dst = toFile(toPath);
		clientManager().getCopyClient().doCopy(sources, dst, false, false, true);
	}

	private File toFile(String path) {
		File dir = new File(getDirectory(), path);
		return dir;
	}

	private void add(File file) throws SVNException {
		clientManager().getWCClient().doAdd(file, false, false, true, SVNDepth.EMPTY, false, true);
	}

	private SVNClientManager clientManager() {
		return scenario().clientManager();
	}

	private Scenario scenario() {
		return _scenario;
	}

}
