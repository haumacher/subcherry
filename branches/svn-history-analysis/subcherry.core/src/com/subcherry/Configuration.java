package com.subcherry;

import java.io.File;

import de.haumacher.common.config.Parser;
import de.haumacher.common.config.Value;
import de.haumacher.common.config.annotate.ValueParser;

/**
 * @version $Revision$ $Author$ $Date$
 */
public interface Configuration extends Value {
	
	long getStartRevision();
	
	long getEndRevision();

	/**
	 * Revision at which the source branch is found for listing relevant changes.
	 */
	long getPegRevision();

	String[] getModules();

	@ValueParser(BranchParser.class)
	String getSourceBranch();

	@ValueParser(BranchParser.class)
	String getTargetBranch();

	File getWorkspaceRoot();
	
	boolean getNoCommit();
	
	boolean getRevert();
	
	boolean getRebase();

	/**
	 * Reorder commits to join "Follow-up" commits to their leading commit.
	 */
	boolean getReorderCommits();

	String getTracURL();
	
	String getSvnURL();
	
	File getPatchDir();

	Long[] getIgnoreRevisions();

	String[] getIgnoreTickets();

	String getTargetMilestone();

	String[] getMilestones();

	Long[] getAdditionalRevisions();

	Long[] getStopOnRevisions();

	String[] getAdditionalTickets();
	
	String getTicketQuery();

	String getBranchPattern();

	boolean getAutoCommit();

	boolean getPreview();
	
	String getPortMessage();

	boolean getDetectCommonModules();

	public class BranchParser implements Parser<String> {

		@Override
		public String parse(String text) {
			if (text == null) {
				return "/";
			}
			if (text.startsWith("/")) {
				return text;
			}
			return "/" + text;
		}

		@Override
		public String unparse(String value) {
			return value;
		}

		@Override
		public boolean equals(String value1, String value2) {
			return value1.equals(value2);
		}

		@Override
		public int hashCode(String value) {
			return value.hashCode();
		}

	}
}
