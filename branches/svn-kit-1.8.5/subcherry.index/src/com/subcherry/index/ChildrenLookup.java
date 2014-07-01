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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ChildrenLookup extends DBHandler {

	private PreparedStatement _statement;

	public ChildrenLookup(Connection connection) throws SQLException {
		super(connection);
		_statement = prepare("SELECT "
				+ Node.COLUMNS
				+ " FROM `node` WHERE `parent`=? AND `rev_min`<=? AND `rev_max`>=?");
	}

	public List<Node> getChildren(long id, long pegRevision) throws SQLException {
		ArrayList<Node> result = new ArrayList<>();

		_statement.setLong(1, id);
		_statement.setLong(2, pegRevision);
		_statement.setLong(3, pegRevision);
		ResultSet resultSet = _statement.executeQuery();
		try {
			while (resultSet.next()) {
				int index = 1;
				long childId = resultSet.getLong(index++);
				long revMin = resultSet.getLong(index++);
				long revMax = resultSet.getLong(index++);
				long parentId = resultSet.getLong(index++);
				long predecessorId = resultSet.getLong(index++);
				int predecessorType = resultSet.getInt(index++);
				String name = resultSet.getString(index++);
				String path = resultSet.getString(index++);
				int nodeType = resultSet.getInt(index++);

				NodeKind nodeKind = NodeKind.values()[nodeType];
				result.add(new Node(childId, revMin, revMax, parentId,
						predecessorId, predecessorType, name, path, nodeKind));
			}
		} finally {
			resultSet.close();
		}

		return result;
	}

}
