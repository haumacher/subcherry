package com.subcherry;

import java.io.File;
import java.util.regex.Pattern;

import de.haumacher.common.config.ObjectParser;
import de.haumacher.common.config.Value;
import de.haumacher.common.config.annotate.ValueParser;

/**
 * @version $Revision$ $Author$ $Date$
 */
public interface Configuration extends MergeConfig, CommitConfig, BranchConfig {
	
	long getStartRevision();

	void setStartRevision(long value);
	
	long getEndRevision();

	void setEndRevision(long value);

	/**
	 * Revision at which the source branch is found for listing relevant changes.
	 */
	long getPegRevision();

	void setPegRevision(long value);

	String[] getModules();

	void setModules(String[] value);

	@ValueParser(BranchParser.class)
	String getSourceBranch();

	void setSourceBranch(String value);

	@ValueParser(BranchParser.class)
	String getTargetBranch();

	void setTargetBranch(String value);

	boolean getNoCommit();

	void setNoCommit(boolean value);
	
	boolean getRebase();

	void setRebase(boolean value);

	/**
	 * Reorder commits to join "Follow-up" commits to their leading commit.
	 */
	boolean getReorderCommits();

	void setReorderCommits(boolean value);

	String getTracURL();

	void setTracURL(String value);
	
	File getPatchDir();

	void setPatchDir(File value);

	Long[] getIgnoreRevisions();

	void setIgnoreRevisions(Long[] value);

	String[] getIgnoreTickets();

	void setIgnoreTickets(String[] value);

	String getTargetMilestone();

	void setTargetMilestone(String value);

	String[] getMilestones();

	void setMilestones(String[] value);

	Long[] getStopOnRevisions();

	void setStopOnRevisions(Long[] value);

	String[] getAdditionalTickets();

	void setAdditionalTickets(String[] value);
	
	String getTicketQuery();

	void setTicketQuery(String value);

	boolean getAutoCommit();

	void setAutoCommit(boolean value);

	boolean getPreview();

	void setPreview(boolean value);
	
	boolean getReintegrate();

	void setReintegrate(boolean value);

	String getPortMessage();

	void setPortMessage(String value);

	boolean getDetectCommonModules();

	void setDetectCommonModules(boolean value);

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

		void setExcludeTicketMilestone(Pattern value);

		@ValueParser(PatternParser.class)
		Pattern getExcludePath();

		void setExcludePath(Pattern value);

	}

	/**
	 * Whether processing does not stop on conflicts.
	 * 
	 * <p>
	 * Requires {@link #getNoCommit()} also be set to <code>true</code>.
	 * </p>
	 */
	boolean getAutoSkipConflicts();

	void setAutoSkipConflicts(boolean value);

	/**
	 * Whether ticket dependency analysis should be skipped.
	 */
	boolean getSkipDependencies();

	void setSkipDependencies(boolean value);

	/**
	 * Whether a merged change that has itself has been merged from somewhere else, is committed as
	 * if it was directly merged from its original source.
	 */
	boolean getSilentRebase();

	void setSilentRebase(boolean value);

	/**
	 * Whether all changes from the {@link #getSourceBranch()} should be merged ignoring ticket
	 * information.
	 */
	boolean getAllChanges();

}
