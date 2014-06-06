package com.subcherry;

import java.io.File;
import java.util.Map;
import java.util.regex.Pattern;

import de.haumacher.common.config.ObjectParser;
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

	@ValueParser(AdditionalRevision.Parser.class)
	Map<Long, AdditionalRevision> getAdditionalRevisions();

	Long[] getStopOnRevisions();

	String[] getAdditionalTickets();
	
	String getTicketQuery();

	String getBranchPattern();

	boolean getAutoCommit();

	boolean getPreview();
	
	String getPortMessage();

	boolean getDetectCommonModules();

	public class BranchParser extends ObjectParser<String> {

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

	}

	DependencyReport getDependencyReport();

	interface DependencyReport extends Value {

		@ValueParser(PatternParser.class)
		Pattern getExcludeTicketMilestone();

		@ValueParser(PatternParser.class)
		Pattern getExcludePath();

	}

	/**
	 * Whether processing does not stop on conflicts.
	 * 
	 * <p>
	 * Requires {@link #getNoCommit()} also be set to <code>true</code>.
	 * </p>
	 */
	boolean getAutoSkipConflicts();

	/**
	 * Whether intra-branch moves (and copies) should be merged semantically (as intra-branch move
	 * in the target branch an applying the changes that happened together with the move).
	 */
	boolean getSemanticMoves();

}
