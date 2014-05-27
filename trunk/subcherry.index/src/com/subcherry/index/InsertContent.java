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

import java.sql.Connection;
import java.sql.SQLException;

public class InsertContent extends Batch {

	private static final String COLUMNS = "`node`, `rev_min`, `rev_max`, `data`";

	public InsertContent(Connection connection) throws SQLException {
		super(connection,
				"INSERT INTO `content` (" + COLUMNS + ") VALUES (?, ?, ?, ?)");
	}

	public void insert(long nodeId, long revMin, long revMax) throws SQLException {
		int index = 1;
		setLong(index++, nodeId);
		setLong(index++, revMin);
		setLong(index++, revMax);
		setLong(index++, 0);
		addBatch();
	}

}
