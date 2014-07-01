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
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PathLookup extends DBHandler implements Closeable {

	private PreparedStatement _statement;

	public PathLookup(Connection connection) throws SQLException {
		super(connection);
		_statement = prepare("SELECT `id` FROM `node` WHERE `path` = ? AND `rev_min` <= ? ORDER BY `rev_min` DESC LIMIT 1");
	}

	public long lookupPath(String path, long revision) throws SQLException {
		int index = 1;
		_statement.setString(index++, path);
		_statement.setLong(index++, revision);
		ResultSet resultSet = _statement.executeQuery();
		try {
			if (resultSet.next()) {
				return resultSet.getLong(1);
			} else {
				throw new IllegalArgumentException("Path '" + path + "' not found in revision '" + revision + "'.");
			}
		} finally {
			resultSet.close();
		}
	}

	@Override
	public void close() throws IOException {
		try {
			_statement.close();
		} catch (SQLException ex) {
			throw SvnSync.toIOException(ex);
		}
	}
}