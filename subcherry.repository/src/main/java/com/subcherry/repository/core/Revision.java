/*
 * TimeCollect records time you spent on your development work.
 * Copyright (C) 2015 Bernhard Haumacher and others
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
package com.subcherry.repository.core;

public class Revision {
	
	public enum Kind {
		HEAD, BASE, WORKING, COMMIT, UNDEFINED
	}

	private final long _commitNumber;

	private Kind _kind;
	
	private Revision(Kind kind, long commitNumber) {
		assert kind != null;

		_kind = kind;
		_commitNumber = commitNumber;
	}

	public final Kind kind() {
		return _kind;
	}

	public static final Revision HEAD = new Revision(Kind.HEAD, -1);
	
	public static final Revision BASE = new Revision(Kind.BASE, -2);

	public static final Revision WORKING = new Revision(Kind.WORKING, -3);

	public static final Revision UNDEFINED = new Revision(Kind.UNDEFINED, Long.MIN_VALUE);

	public static Revision create(long commitNumber) {
		assert commitNumber > 0;
		return new Revision(Kind.COMMIT, commitNumber);
	}

	public long getNumber() {
		return _commitNumber;
	}

	@Override
	public String toString() {
		if (kind() == Kind.COMMIT) {
			return "r" + getNumber();
		} else {
			return kind().toString();
		}
	}

}
