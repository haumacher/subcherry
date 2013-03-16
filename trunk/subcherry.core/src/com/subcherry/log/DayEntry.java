package com.subcherry.log;

import java.util.HashMap;
import java.util.Map;

public class DayEntry {

	public String dayString;
	public Map<String, TaskEntry> tasks = new HashMap<String, TaskEntry>();
	
	public DayEntry(String dayString) {
		this.dayString = dayString;
	}
	
	public TaskEntry getTask(String ticket) {
		TaskEntry result = tasks.get(ticket);
		if (result == null) {
			result = new TaskEntry(ticket);
			tasks.put(ticket, result);
		}
		return result;
	}
	
}
