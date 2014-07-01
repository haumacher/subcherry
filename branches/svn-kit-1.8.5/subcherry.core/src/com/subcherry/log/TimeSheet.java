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
