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
package com.subcherry.history;

import java.util.Date;

public class Change {

	private final Long _revision;

	private final String _author;

	private final Date _date;

	private final String _message;

	Change(Long revision, String author, Date date, String message) {
		_revision = revision;
		_author = author;
		_date = date;
		_message = message;
	}

	public Long getRevision() {
		return _revision;
	}

	public String getAuthor() {
		return _author;
	}

	public Date getDate() {
		return _date;
	}

	public String getMessage() {
		return _message;
	}

}