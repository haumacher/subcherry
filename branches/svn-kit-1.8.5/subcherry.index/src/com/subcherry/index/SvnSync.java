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
package com.subcherry.index;

import static com.subcherry.Globals.*;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import com.subcherry.Main;
import com.subcherry.index.db.DB;

/**
 * Synchronize the remove SVN repository with a local database index that allows navigating back and
 * forth in history.
 * 
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 * @version $Revision$ $Author$ $Date$
 */
public class SvnSync {

	private static final String[] NO_PROPERTIES = {};

	final Connection _connection;

	public SvnSync(Connection connection) {
		_connection = connection;
	}

	public static void main(String[] args) throws SVNException, IOException, SQLException {
		DataSource dataSource = DB.createDataSource();
		
		Connection connection = dataSource.getConnection();
		try {
			connection.setAutoCommit(false);
			new SvnSync(connection).run();
		} finally {
			connection.close();
		}
	}
	
	private void run() throws SQLException, SVNException, IOException {
		
		class Handler implements ISVNLogEntryHandler, Closeable {
			
			private IndexBuilder _index;

			public Handler(IndexBuilder index) throws SQLException {
				_index = index;
			}

			@Override
			public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
				_index.insert(logEntry);
			}

			@Override
			public void close() throws IOException {
				_index.close();
			}
		}

		SVNURL rootUrl = SVNURL.parseURIDecoded(config().getSvnURL());

		SVNClientManager svnClient = Main.newSVNClientManager();
		SVNLogClient logClient = svnClient.getLogClient();
		String[] paths = null;
		// SVNRevision endRevision = SVNRevision.create(10000);
		SVNRevision endRevision = SVNRevision.HEAD;
		SVNRevision startRevision = SVNRevision.create(1);
		SVNRevision pegRevision = SVNRevision.HEAD;
		boolean discoverChangedPaths = true;

		new DBReset(_connection).run();
		IndexBuilder index = new IndexBuilder(_connection);
		Handler handler = new Handler(index);
		logClient.doLog(rootUrl, paths, pegRevision, startRevision, endRevision, false, discoverChangedPaths, true,
			0, NO_PROPERTIES, handler);
		index.close();
		_connection.commit();
	}

	static IOException toIOException(SQLException ex) {
		return new IOException(ex);
	}

	static String quote(String message) {
		return message.replace("\n", "\\n").replace("\r", "\\r");
	}

}
