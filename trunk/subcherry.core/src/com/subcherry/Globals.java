/*
 * TimeCollect records time you spent on your development work.
 * Copyright (C) 2014 Bernhard Haumacher and others
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.subcherry;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.haumacher.common.config.PropertiesUtil;

public class Globals {

	private static final Logger LOG = logger(Globals.class);

	private static Configuration _config;

	public static Configuration config() {
		return _config;
	}

	public static Logger logger(Class<?> clazz) {
		return Logger.getLogger(clazz.getName());
	}

	static {
		try {
			installConfiguration();
		} catch (IOException ex) {
			LOG.log(Level.SEVERE, "Cannot load global configuration.", ex);
			System.exit(1);
		}
	}

	private static void installConfiguration() throws IOException {
		if (config() == null) {
			setConfig(PropertiesUtil.load("conf/configuration.properties", Configuration.class));
		}
	}

	public static void reloadConfig() throws IOException {
		PropertiesUtil.load("conf/configuration.properties", _config);
	}

	private static void setConfig(Configuration config) {
		_config = config;
	}

}
