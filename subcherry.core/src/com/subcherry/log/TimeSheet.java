/*
 * SubCherry - Cherry Picking with Trac and Subversion
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
package com.subcherry.log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TimeSheet {

	private final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");
	public final Map<String, DayEntry> entries = new HashMap<String, DayEntry>();
	
	public DayEntry getEntry(Date date) {
		String dayString = dayFormat.format(date);
		DayEntry result = entries.get(dayString);
		if (result == null) {
			result = new DayEntry(dayString);
			entries.put(dayString, result);
		}
		return result;
	}
	
}
