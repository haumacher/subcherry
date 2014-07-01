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

public class InsertNode extends Batch {

	public InsertNode(Connection connection) throws SQLException {
		super(connection,
				"INSERT INTO `node` (" + Node.COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
	}

	public void insert(Node node) throws SQLException {
		int index = 1;
		setLong(index++, node.getId());
		setLong(index++, node.getRevMin());
		setLong(index++, node.getRevMax());
		setLong(index++, node.getParentId());
		setLong(index++, node.getPredecessorId());
		setInt(index++, node.getPredecessorType());
		setString(index++, node.getName());
		setString(index++, node.getPath());
		setInt(index++, node.getNodeKind().ordinal());
		addBatch();
	}

}
