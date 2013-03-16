package com.subcherry.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

public class BlameAll {

	public static class Counter {
		
		private long count;

		public void inc() {
			count++;
		}
		
		public long getCount() {
			return count;
		}
		
		@Override
		public String toString() {
			return Long.toString(count);
		}

	}

	private static SVNLogClient _listClient;
	private static SVNRevision _pathRevision;
	private static SVNRevision _startRevision;
	private static SVNRevision _endRevision;
	private static SVNLogClient _annotateClient;

	public static void main(String[] args) throws SVNException, IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");

		File settingsFile = new File("settings.properties");
		Properties globalProperties = SettingsUtil.loadConfig(settingsFile);
		SVNSettings svn = SVNSettingsImpl.loadSettings(in, globalProperties);
		
		Properties updatedProperties = new Properties();
		SVNSettingsImpl.saveSettings(svn, updatedProperties);
		SettingsUtil.saveConfig(settingsFile, updatedProperties);
		
		DAVRepositoryFactory.setup();
		ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(svn.getUser(), svn.getPassword());

		DefaultSVNOptions options = new DefaultSVNOptions();
		SVNClientManager clientManager1 = SVNClientManager.newInstance(options, authManager);
		SVNClientManager clientManager2 = SVNClientManager.newInstance(options, authManager);
		
		// Two separate clients are required, because recursively descending a
		// directory may not be interleaved with other calls to the same client,
		// because descending depends on some global state in the client.
		_listClient = clientManager1.getLogClient();
		_annotateClient = clientManager2.getLogClient();
		
		_pathRevision = SVNRevision.HEAD;
		_startRevision = SVNRevision.create(1);
		_endRevision = SVNRevision.HEAD;
		
		SVNURL url = SVNURL.parseURIDecoded(svn.getUrl());

		ISVNDirEntryHandler listHandler = new ISVNDirEntryHandler() {
			@Override
			public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
				if (dirEntry.getKind() == SVNNodeKind.FILE) {
					if (dirEntry.getName().endsWith(".java")) {
						System.out.println(dirEntry.getRelativePath());
						
						SVNURL fileUrl = dirEntry.getURL();
						annotate(fileUrl);
						
						ArrayList<Entry<String, Counter>> hitlist = new ArrayList<Entry<String, Counter>>(_authorLines.entrySet());
						Collections.sort(hitlist, new Comparator<Entry<String, Counter>>() {

							@Override
							public int compare(Entry<String, Counter> e1, Entry<String, Counter> e2) {
								long c1 = e1.getValue().getCount();
								long c2 = e2.getValue().getCount();
								if (c1 < c2) {
									return 1;
								}
								else if (c1 > c2) {
									return -1;
								} 
								else {
									return 0;
								}
							}
						});
						System.out.println(hitlist);
					}
				}
			}
		};
		_listClient.doList(url, _pathRevision, _endRevision, /*fetchLocks*/false, /*recursive*/true, listHandler);
	}

	protected static long _lineCount;
	protected static Map<String, Counter> _authorLines = new HashMap<String, Counter>();

	protected static void annotate(SVNURL fileUrl) throws SVNException {
		ISVNAnnotateHandler annotateHandler = new ISVNAnnotateHandler() {

			@Override
			public boolean handleRevision(Date date, long revision, String author, File contents) throws SVNException {
				return false;
			}

			@Override
			public void handleLine(Date date, long revision, String author, String line, Date mergedDate,
				long mergedRevision, String mergedAuthor, String mergedPath, int lineNumber) throws SVNException {
				
				_lineCount++;
				Counter cnt = _authorLines.get(author);
				if (cnt == null) {
					_authorLines.put(author, cnt = new Counter());
				}
				
				cnt.inc();
			}

			@Override
			public void handleEOF() {
				// Ignore.
			}

			@Override
			@Deprecated
			public void handleLine(Date date, long revision, String author, String line) throws SVNException {
				throw new UnsupportedOperationException();
			}
			
		};
		_annotateClient.doAnnotate(fileUrl, _pathRevision, _startRevision, _endRevision, annotateHandler);
	}
}
