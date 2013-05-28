package com.subcherry;

import java.io.File;

import de.haumacher.common.config.Value;

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

	String getSourceBranch();

	String getTargetBranch();

	File getWorkspaceRoot();
	
	boolean getNoCommit();
	
	boolean getRevert();
	
	boolean getPortHotfixes();

	String getTracURL();
	
	String getSvnURL();
	
	File getPatchDir();

	Long[] getIgnoreRevisions();

	String[] getIgnoreTickets();

	String getTargetMilestone();

	String[] getMilestones();

	Long[] getAdditionalRevisions();

	String[] getAdditionalTickets();
	
	String getTicketQuery();

	String getBranchPattern();

	boolean getAutoCommit();

	boolean getPreview();
	
	String getPortMessage();

	boolean getDetectCommonModules();
}
