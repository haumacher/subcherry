package com.subcherry.log;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import com.subcherry.trac.TracConnection;
import com.subcherry.trac.TracTicket;

public class Log {
	private static PrintStream out = System.out;
	private static Map<String, String> types;
	static {
		HashMap<String, String> map = new HashMap<String, String>();
		
		map.put("defect", "Bugfix");
		map.put("enhancement", "Erweiterung");
		map.put("task", "");
		
		types = map;
	}

	private static Map<String, String> components;
	static {
		HashMap<String, String> map = new HashMap<String, String>();
		
		map.put("tl", "Top-Logic");
		map.put("dpm", "DPM");
		map.put("top-s", "TOP-S");
		
		components = map;
	}

	public static void main(String[] args) throws SVNException, IOException, ParseException {

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		System.out.print("User name: ");
		final String user = in.readLine();
		
		System.out.print("Password (will be echoed): ");
		final String password = in.readLine();
		
		System.out.print("Von Datum (YYYY-MM-DD): ");
		final String from = in.readLine();
		
		System.out.print("Bis Datum (YYYY-MM-DD): ");
		final String to = in.readLine();

		System.out.print("Dateiname: ");
		final String fileName = in.readLine();
		
		String tracUser = user; 
		String tracPassword = password; 
		
		TracConnection trac = new TracConnection("http://10.49.8.8/trac/login/xmlrpc", tracUser, tracPassword);
		
		if (fileName.length() == 0) {
			out = System.out;
		} else {
			out = new PrintStream(new FileOutputStream(fileName));
		}
		
		DAVRepositoryFactory.setup();
		ISVNAuthenticationManager authManager = SVNWCUtil
				.createDefaultAuthenticationManager(user, password);

		SVNURL url = SVNURL.parseURIDecoded("http://10.49.8.8/svn/top-logic");
		SVNRepository repository = SVNRepositoryFactory.create(url, null);

		//set an auth manager which will provide user credentials
		repository.setAuthenticationManager(authManager);

		SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");
		
		Date startDate = dayFormat.parse(from);
		Date endDate = dayFormat.parse(to);
		
		long startRevision = repository.getDatedRevision(startDate);
		long endRevision = repository.getDatedRevision(endDate);

		Collection log = repository.log(
			new String[] {
				"/",
			}, null, startRevision, endRevision, true, true);

		Pattern ticketPattern = Pattern.compile("^Ticket #(\\d+):\\s+(.*)\\s*$", Pattern.DOTALL);
		TimeSheet sheet = new TimeSheet();
		
		for (Iterator entries = log.iterator(); entries.hasNext();) {
			SVNLogEntry logEntry = (SVNLogEntry) entries.next();
			
			if (! logEntry.getAuthor().equals(user)) {
				continue;
			}
			
			String message = logEntry.getMessage();
			Matcher matcher = ticketPattern.matcher(message);
			
			String ticket;
			String detail;
			String title;
			String component;
			String type;
			if (matcher.matches()) {
				ticket = "Ticket #" + matcher.group(1);
				detail = matcher.group(2);
				TracTicket tracTicket = TracTicket.getTicket(trac, Integer.parseInt(matcher.group(1)));
				
				if (((String) tracTicket.getAttributeValue(TracTicket.TICKET_ATT_KEYWORD)).contains("CodeStyle")) {
					continue;
				}
				
				title = (String) tracTicket.getAttributeValue(TracTicket.TICKET_ATT_SUMMARY);
				component = getComponentName((String) tracTicket.getAttributeValue(TracTicket.TICKET_ATT_COMPONENT));
				type = getTypeName((String) tracTicket.getAttributeValue(TracTicket.TICKET_ATT_TYPE));
			} else {
				ticket = message;
				detail = "";
				title = "";
				component = "";
				type = "";
			}

			Date date = logEntry.getDate();
			DayEntry day = sheet.getEntry(date);
			
			TaskEntry task = day.getTask(ticket);
			task.title = title;
			task.component = component;
			task.type = type;
			
			task.revisions.add(logEntry.getRevision());
		}
		
		write('"');
		write("Datum");
		write('"');
		write(',');
		
		write('"');
		write("Projekt");
		write('"');
		write(',');
		
		write('"');
		write("Arbeitspaket");
		write('"');
		write(',');
		
		write('"');
		write("Tätigkeit");
		write('"');
		write(',');
		
		write('"');
		write("Tätigkeitsprofil");
		write('"');
		write(',');
		
		write('"');
		write("Ticket");
		write('"');
		write(',');
		
		write('"');
		write("Beschreibung");
		write('"');
		write(',');
		
		write('"');
		write("Stunden");
		write('"');
		write(',');
		
		write('"');
		write("Revisions");
		write('"');

		write('\n');

		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(startDate);
		while (true) {
			Date currentDay = cal.getTime();
			if (currentDay.compareTo(endDate) > 0) {
				break;
			}
			String dayString = dayFormat.format(currentDay);
			DayEntry dayEntry = sheet.entries.get(dayString);
			
			if (dayEntry == null) {
				if ((cal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) && (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY))  {
					write(dayString);
				}
				write('\n');
			} else {
				ArrayList<TaskEntry> tasks = new ArrayList<TaskEntry>(dayEntry.tasks.values());
				Collections.sort(tasks, new Comparator<TaskEntry>() {
					@Override
					public int compare(TaskEntry o1, TaskEntry o2) {
						return o1.ticket.compareTo(o2.ticket);
					}
				});
				
				for (TaskEntry taskEntry : tasks) {
					write(dayEntry.dayString);
					write(',');
					
					write('"');
					write(taskEntry.component);
					write('"');
					write(',');
					
					write(',');
					
					write('"');
					write("Implementierung");
					write('"');
					write(',');
					
					write('"');
					write(taskEntry.type);
					write('"');
					write(',');
					
					if (taskEntry.ticket.startsWith("Ticket #")) {
						write('"');
						write(taskEntry.ticket);
						write('"');
					}
					write(',');
					
					write('"');
					if (taskEntry.ticket.startsWith("Ticket #")) {
						write(taskEntry.title);
					} else {
						write(taskEntry.ticket);
					}
					write('"');
					write(',');
					
					write(',');
					
					write('"');
					for (Long rev : taskEntry.revisions) {
						write('r');
						write(Long.toString(rev));
						write(' ');
					}
					write('"');

					write('\n');
				}
			}
			
			cal.add(Calendar.DAY_OF_YEAR, 1);
		}
		
		out.close();
	}
	
	public static String getTypeName(String tracType) {
		String result = types.get(tracType);
		if (result == null) {
			return tracType;
		} else {
			return result;
		}
	}

	public static String getComponentName(String tracComponent) {
		String result = components.get(tracComponent);
		if (result == null) {
			return tracComponent;
		} else {
			return result;
		}
	}

	private static void write(char string) {
		out.print(string);
	}
	
	private static void write(String string) {
		out.print(string);
	}
	
	
}
