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
package com.subcherry.index.db;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

import com.subcherry.Globals;

import de.haumacher.common.config.PropertiesUtil;
import de.haumacher.common.config.Value;

public class DB {
	
	private static final Logger LOG = Globals.logger(DB.class);

	public interface Config extends Value {

		String getHost();

		int getPort();

		String getUser();

		String getPassword();

		String getSchema();
		
	}
	
	private static final Config CONF;
	
	static {
		Config conf = null;
		try {
			conf = PropertiesUtil.load("conf/db.properties", Config.class);
		} catch (IOException ex) {
			LOG.log(Level.SEVERE, "Cannot load db configuration.", ex);
			System.exit(1);
		}
		CONF = conf;
	}
	
	public static DataSource createDataSource() {
		
		String host = CONF.getHost();
		int port = CONF.getPort();
		String user = CONF.getUser();
		String password = CONF.getPassword();
		String schema = CONF.getSchema();
		
		MysqlDataSource ds = new MysqlDataSource();
		ds.setServerName(host);
		ds.setPort(port);
		ds.setUser(user);
		ds.setPassword(password);
		ds.setDatabaseName(schema);
		ds.setRewriteBatchedStatements(true);
		ds.setUseServerPreparedStmts(true);
		return ds;
	}

}
