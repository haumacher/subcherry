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
package com.subcherry;

import java.util.Comparator;

import com.subcherry.history.Change;

public class ChangeOrder implements Comparator<Change> {

	public static final Comparator<Change> INSTANCE = new ChangeOrder();

	private ChangeOrder() {
		// Singleton.
	}

	@Override
	public int compare(Change c1, Change c2) {
		return c1.getRevision().compareTo(c2.getRevision());
	}

}
