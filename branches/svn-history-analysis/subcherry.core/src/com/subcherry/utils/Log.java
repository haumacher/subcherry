package com.subcherry.utils;

import java.util.logging.Logger;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class Log {

	public static void info(String info) {
		Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).info(info);
	}

	public static void warning(String message) {
		Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(message);
	}
	
}

