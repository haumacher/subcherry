package com.subcherry.dependency;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import com.subcherry.Main;
import com.subcherry.SVNConfig;
import com.subcherry.diff.Chunk;
import com.subcherry.diff.Diff;
import com.subcherry.diff.DiffParser;
import com.subcherry.diff.Line;

import de.haumacher.common.config.PropertiesUtil;
import de.haumacher.common.config.Value;

/**
 * Tool to find all dependencies of a ticket.
 * 
 * <p>
 * Dependencies of a ticket are those tickets that have changed source code
 * lines in the direct neighborhood of modifications done in the ticket in
 * question.
 * </p>
 * 
 * @version $Revision$ $Author$ $Date$
 */
public class TicketDependencies {
	
	static final Logger LOG = Logger.getLogger(TicketDependencies.class.getName());
	
	public interface DependencyOptions extends Value {
		String getPath();

		long getPegRev();

		long getStartRev();

		long getStopRev();
	}

	private static final String[] NO_PROPERTIES = {};

	public static void main(String[] args) throws IOException, SVNException {
		LOG.setLevel(Level.FINE);
		ConsoleHandler logHandler = new ConsoleHandler();
		logHandler.setLevel(Level.FINE);
		LOG.addHandler(logHandler);
		
		SVNClientManager svnClient = Main.newSVNClientManager();
		SVNDiffClient diffClient = svnClient.getDiffClient();
		
		SVNConfig config = PropertiesUtil.load("conf/svnConfig.properties", SVNConfig.class);
		DependencyOptions options = PropertiesUtil.load("conf/dependencyOptions.properties", DependencyOptions.class);
	
		SVNURL url = SVNURL.parseURIDecoded(config.getSvnURL() + options.getPath());
		SVNRevision pegRev = SVNRevision.create(options.getPegRev());
		SVNRevision startRev = SVNRevision.create(options.getStartRev());
		SVNRevision stopRev = SVNRevision.create(options.getStopRev());
		
		ByteArrayOutputStream diffBuffer = new ByteArrayOutputStream();
		diffClient.doDiff(url, pegRev, startRev, stopRev, SVNDepth.EMPTY, true, diffBuffer);
		
		ISVNDiffGenerator diffGenerator = diffClient.getDiffGenerator();
		String encoding = diffGenerator.getEncoding();
		final byte[] eol = diffGenerator.getEOL();
		final byte[] byteBuffer = diffBuffer.toByteArray();
		final Charset charset = Charset.forName(encoding);
				
		Diff diff = DiffParser.parse(split(byteBuffer, eol, charset));

		final Set<Integer> relevantLines = getDependencyLines(diff);
		
		class DependencyRevisionCollector implements ISVNAnnotateHandler {
			
			Set<Long> dependencyRevisions = new HashSet<Long>();

			@Deprecated
			@Override
			public void handleLine(Date date, long revision, String author, String line) throws SVNException {
				throw new UnsupportedOperationException();
			}

			@Override
			public void handleLine(Date date, long revision, String author,
					String line, Date mergedDate, long mergedRevision,
					String mergedAuthor, String mergedPath, int lineNumber)
					throws SVNException {
				if (relevantLines.contains(lineNumber)) {
					dependencyRevisions.add(revision);
				}
			}

			@Override
			public boolean handleRevision(Date date, long revision, String author, File contents) throws SVNException {
				return true;
			}

			@Override
			public void handleEOF() {
				// Ignore.
			}
			
			public Set<Long> getDependencyRevisions() {
				return dependencyRevisions;
			}
		};
		
		DependencyRevisionCollector annotate = new DependencyRevisionCollector();
		svnClient.getLogClient().doAnnotate(url, pegRev, SVNRevision.create(1), startRev, annotate);
		
		ArrayList<Long> revisions = new ArrayList<Long>(annotate.getDependencyRevisions());
		Collections.sort(revisions);
		System.out.println(revisions);
	}

	private static Set<Integer> getDependencyLines(Diff diff) {
		HashSet<Integer> result = new HashSet<Integer>();
		for (Chunk chunk : diff.getChunks()) {
			int nr = chunk.getStart1();
			for (Line line : chunk.getLines()) {
				switch (line.getOperation()) {
				case TAKE: {
					nr++;
					break;
				}
				case ADD: {
					break;
				}
				case DELETE: {
					result.add(nr);
					nr++;
				}
				}
			}
		}
		return result;
	}

	private static Iterable<String> split(final byte[] byteBuffer, final byte[] eol, final Charset charset) {
		return Arrays.asList(new String(byteBuffer, charset).split("\\r?\\n|\\r"));
	}
	
	private static Iterable<String> split2(final byte[] byteBuffer, final byte[] eol, final Charset charset) {
		Iterable<String> lines = new Iterable<String>() {
			@Override
			public Iterator<String> iterator() {
				return new Iterator<String>() {
					int length = byteBuffer.length;
					int start = 0;
					int eolSize = eol.length;

					@Override
					public boolean hasNext() {
						return start < length;
					}

					@Override
					public String next() {
						if (start >= length) {
							throw new NoSuchElementException();
						}
						
						int stop = start;
						findEol:
						for (; stop < length; stop++) {
							for (int n = stop, m = 0; m < eolSize; n++, m++) {
								if (byteBuffer[n] != eol[m]) {
									continue findEol;
								}
							}
							
							break;
						}
						
						String result = new String(byteBuffer, start, stop - start, charset);
						start = stop + eolSize;
						return result;
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
		return lines;
	}	
}

