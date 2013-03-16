package com.subcherry;

import java.io.File;

import com.subcherry.configuration.properties.PropertyConfiguration;

/**
 * @version $Revision$ $Author$ $Date$
 */
public interface Configuration extends PropertyConfiguration {
	
	long getStartRevision();
	
	long getEndRevision();

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