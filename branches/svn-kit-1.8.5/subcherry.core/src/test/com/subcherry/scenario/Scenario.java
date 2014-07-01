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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;

import com.subcherry.merge.LastLogEntry;

public class Scenario extends FileSystem {

	private SVNClientManager _clientManager;
	private SVNURL _repositoryUrl;

	private int _id = 1;

	private int _fileId = 1;

	private static int _scenarioId = 1;

	public static Scenario scenario() throws IOException, SVNException {
		File repositoryRoot = File.createTempFile("repos", "");
		repositoryRoot.delete();
		repositoryRoot.mkdir();

		SVNClientManager clientManager = SVNClientManager.newInstance();
		SVNURL repositoryUrl =
			clientManager.getAdminClient().doCreateRepository(repositoryRoot, "uuid-" + (_scenarioId++), true, false);

		return new Scenario(clientManager, repositoryUrl);
	}

	private Scenario(SVNClientManager clientManager, SVNURL repositoryUrl) {
		_clientManager = clientManager;
		_repositoryUrl = repositoryUrl;
	}

	public SVNURL getRepositoryUrl() {
		return _repositoryUrl;
	}

	public SVNClientManager clientManager() {
		return _clientManager;
	}

	public WC wc(String path) throws IOException, SVNException {
		return new WC(this, path);
	}

	public SVNLogEntry log(long revision) throws SVNException {
		String[] paths = { "/" };
		SVNRevision startRevision = SVNRevision.create(revision);
		SVNRevision endRevision = SVNRevision.create(revision);
		SVNRevision pegRevision = startRevision;
		LastLogEntry handler = new LastLogEntry();
		clientManager().getLogClient().doLog(getRepositoryUrl(), paths, pegRevision, startRevision, endRevision, false,
			true, false, 0, null, handler);
		return handler.getLogEntry();
	}

	@Override
	public void mkdir(String path) throws SVNException {
		SVNURL url = getRepositoryUrl().appendPath(path, true);
		commitClient().doMkDir(new SVNURL[] { url }, createMessage(), null, true);
	}

	@Override
	public void file(String path) throws IOException, SVNException {
		File dir = File.createTempFile("upload", "");
		dir.delete();
		dir.mkdir();
		File file = new File(dir, fileName(path));
		fillFileContent(file);
		SVNURL dstURL = getRepositoryUrl().appendPath(path, true);
		commitClient().doImport(file, dstURL, createMessage(), null, false, true, SVNDepth.FILES);
	}

	static String fileName(String path) {
		int separator = path.lastIndexOf('/');
		if (separator < 0) {
			return path;
		} else {
			return path.substring(separator + 1);
		}
	}

	@Override
	public void copy(String toPath, String fromPath) throws SVNException {
		SVNURL srcUrl = getRepositoryUrl().appendPath(fromPath, true);
		SVNCopySource source = new SVNCopySource(SVNRevision.HEAD, SVNRevision.HEAD, srcUrl);
		SVNCopySource[] sources = { source };
		SVNURL dst = getRepositoryUrl().appendPath(toPath, true);
		copyClient().doCopy(sources, dst, false, false, true, createMessage(), null);
	}

	private SVNCopyClient copyClient() {
		return clientManager().getCopyClient();
	}

	public String createMessage() {
		return "Commit " + createCommitId() + ".";
	}

	int createCommitId() {
		return _id++;
	}

	void fillFileContent(File file) throws FileNotFoundException, IOException {
		FileOutputStream out = new FileOutputStream(file);
		try {
			Writer writer = new OutputStreamWriter(out, Charset.forName("utf-8"));
			writer.write(createFileContent());
			writer.close();
		} finally {
			out.close();
		}
	}

	String createFileContent() {
		return "File " + createFileId();
	}

	int createFileId() {
		return _fileId++;
	}

	private SVNCommitClient commitClient() {
		return _clientManager.getCommitClient();
	}

}
