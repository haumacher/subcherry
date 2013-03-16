package com.subcherry;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import com.subcherry.configuration.ConfigurationFactory;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class Restart {

	public static final long NO_REVISION_FOUND = -1;
	
	private static final String REVISION_PROPERTY = "revision";
	private static final String FILE_NAME = "conf/restart.properties";

	public static void setRevision(long revision) throws IOException {
		Properties p = new Properties();
		p.setProperty(REVISION_PROPERTY, Long.toString(revision));
		ConfigurationFactory.storeProperties(p, FILE_NAME, "\n\tProperties are used by merge Tool to restart merge at stored revision.\n\tThe file is deleted after completing merge.\n\tDo not modify.\n\n\tDelete when configured revision should be used.\n");
	}
	
	public static long getRevision() {
		try {
			Properties properties = ConfigurationFactory.readProperties(FILE_NAME);
			String nextRevision = properties.getProperty(REVISION_PROPERTY);
			return Long.parseLong(nextRevision);
		} catch (IOException ex) {
			return NO_REVISION_FOUND;
		}
	}
	
	public static void clear() {
		File f = new File(FILE_NAME);
		f.delete();
	}
	
	

}

