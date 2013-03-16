package com.subcherry;

import de.haumacher.common.config.Value;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public interface SVNConfig extends Value {
	String getSvnURL();
	String getTicketPattern();
	
	String getTracURL();
}
