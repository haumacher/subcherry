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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNWCClient;

public class WC extends FileSystem {

	private Scenario _scenario;

	private String _path;

	private SVNURL _baseUrl;

	private File _wcPath;

	WC(Scenario scenario, String path) throws IOException, SVNException {
		_scenario = scenario;
		_path = path;

		_wcPath = File.createTempFile("workspace", "");
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
	public long mkdir(String path) throws SVNException {
		File dir = toFile(path);
		dir.mkdirs();
		add(dir);
		return -1;
	}

	@Override
	public long file(String path) throws SVNException, IOException {
		File file = toFile(path);
		scenario().fillFileContent(file);
		add(file);
		return -1;
	}

	public void setProperty(String path, String name, String value) throws SVNException {
		File file = toFile(path);
		clientManager().getWCClient().doSetProperty(file, name, SVNPropertyValue.create(value), false, SVNDepth.EMPTY,
			null, null);
	}

	@Override
	public long copy(String toPath, String fromPath) throws SVNException {
		return internalCopy(toPath, fromPath, SVNRevision.BASE);
	}

	@Override
	public long copy(String toPath, String fromPath, long revision) throws SVNException {
		return internalCopy(toPath, fromPath, SVNRevision.create(revision));
	}

	private long internalCopy(String toPath, String fromPath, SVNRevision rev) throws SVNException {
		SVNCopySource[] sources = { new SVNCopySource(SVNRevision.BASE, rev, toFile(fromPath)) };
		File dst = toFile(toPath);
		clientManager().getCopyClient().doCopy(sources, dst, false, false, true);
		return -1;
	}

	public void copyFromRemote(String toPath, String remoteFromPath, long revision) throws SVNException {
		SVNRevision svnRevision = SVNRevision.create(revision);
		SVNCopySource[] sources =
			{ new SVNCopySource(svnRevision, svnRevision, scenario().getRepositoryUrl()
				.appendPath(remoteFromPath, true)) };
		File dst = toFile(toPath);
		clientManager().getCopyClient().doCopy(sources, dst, false, false, true);
	}

	private File toFile(String path) {
		File dir = new File(getDirectory(), path);
		return dir;
	}

	private void add(File file) throws SVNException {
		wcClient().doAdd(file, false, false, true, SVNDepth.EMPTY, false, true);
	}

	private SVNClientManager clientManager() {
		return scenario().clientManager();
	}

	private Scenario scenario() {
		return _scenario;
	}

	public void delete(String path) throws SVNException {
		wcClient().doDelete(toFile(path), false, true, false);
	}

	private SVNWCClient wcClient() {
		return clientManager().getWCClient();
	}

	public void update(String path) throws IOException {
		scenario().fillFileContent(toFile(path));
	}

	public String load(String path) throws IOException {
		FileInputStream in = new FileInputStream(toFile(path));
		try {
			CharBuffer target = CharBuffer.allocate(500);
			Reader r = new InputStreamReader(in);
			try {
				while (r.read(target) >= 0) {
					// Read until the end of file is reached.
				}
			} finally {
				r.close();
			}
			target.flip();
			return target.toString();
		} finally {
			in.close();
		}
	}

	public String getProperty(String path, String property) throws SVNException {
		SVNWCClient wcClient = scenario().clientManager().getWCClient();
		SVNPropertyData data =
			wcClient.doGetProperty(new File(_wcPath, path), property, SVNRevision.WORKING, SVNRevision.WORKING);
		if (data == null) {
			return null;
		}
		return data.getValue().getString();
	}

	public List<File> getModified() throws SVNException {
		final List<File> modified = new ArrayList<>();
		ISVNStatusHandler handler = new ISVNStatusHandler() {
			@Override
			public void handleStatus(SVNStatus status) throws SVNException {
				File file = status.getFile();
				modified.add(file);
			}
		};
		clientManager().getStatusClient().
			doStatus(getDirectory(), SVNRevision.HEAD, SVNDepth.INFINITY, false, false, false, false, handler, null);
		return modified;
	}

	public long merge(String branch, long revision, List<String> mergedModules) throws SVNException {
		SVNURL sourceBranch = scenario().getRepositoryUrl().appendPath(branch, true);
		SVNRevision baseRevision = SVNRevision.create(revision - 1);
		SVNRevision svnRevision = SVNRevision.create(revision);
		SVNRevision pegRevision = svnRevision;
		Collection<SVNRevisionRange> rangesToMerge = Arrays.asList(new SVNRevisionRange(baseRevision, svnRevision));
		for (String module : mergedModules) {
			SVNURL sourceModule = sourceBranch.appendPath(module, true);
			File targetModule = new File(getDirectory(), module);
			clientManager().getDiffClient().doMerge(sourceModule, pegRevision, rangesToMerge, targetModule,
				SVNDepth.INFINITY, true, false, false, false);
		}
		return commit();
	}

}
