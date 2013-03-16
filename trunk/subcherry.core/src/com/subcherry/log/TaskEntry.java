package com.subcherry.log;

import java.util.ArrayList;
import java.util.List;

public class TaskEntry {

	public String ticket;
	public String title;
	public List<Long> revisions = new ArrayList<Long>();
	public String component;
	public String type;
	
	public TaskEntry(String ticket) {
		this.ticket = ticket;
	}
	
}
