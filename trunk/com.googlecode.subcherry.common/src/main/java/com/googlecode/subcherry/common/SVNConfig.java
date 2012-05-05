package com.googlecode.subcherry.common;

/**
 * Settings for a SVN repository. 
 * 
 * @version   $Revision: $  $Author: $  $Date: $
 */
public interface SVNConfig {
	/**
	 * The root URL of the repository.
	 */
	String getRepositoryURL();
	
	/**
	 * A regular expression selecting the ticket number as Group 1 from the
	 * beginning of a commit message.
	 */
	String getTicketPattern();
}
