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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class Batch implements Closeable, Flushable {

	private static final int DEFAULT_BATCH_SIZE = 1000;

	private final PreparedStatement _statement;

	private final int _batchSize;

	private int _cnt = 0;

	public Batch(Connection connection, String sql) throws SQLException {
		this(connection, sql, DEFAULT_BATCH_SIZE);
	}

	public Batch(Connection connection, String sql, int batchSize) throws SQLException {
		_batchSize = batchSize;
		_statement = connection.prepareStatement(sql);
	}

	public PreparedStatement statement() {
		return _statement;
	}

	public void setInt(int index, int x) throws SQLException {
		_statement.setInt(index, x);
	}

	public void setLong(int index, long x) throws SQLException {
		_statement.setLong(index, x);
	}

	public void setString(int index, String x) throws SQLException {
		_statement.setString(index, x);
	}

	public void setTimestamp(int index, Timestamp x) throws SQLException {
		_statement.setTimestamp(index, x);
	}

	public void addBatch() throws SQLException {
		_statement.addBatch();
		_cnt++;

		if (_cnt == _batchSize) {
			internalFlush();
		}
	}

	@Override
	public void flush() throws IOException {
		try {
			internalFlush();
		} catch (SQLException ex) {
			throw SvnSync.toIOException(ex);
		}
	}

	private void internalFlush() throws SQLException {
		if (_cnt > 0) {
			beforeExecute(_cnt);
			handleResult(_statement.executeBatch());
			_cnt = 0;
		}
	}

	protected void beforeExecute(int cnt) throws SQLException {
		// Ignore.
	}

	protected void handleResult(int[] batchResult) throws SQLException {
		// Ignore.
	}

	@Override
	public void close() throws IOException {
		flush();
	}

}