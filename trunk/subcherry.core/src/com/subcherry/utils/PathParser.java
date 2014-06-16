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
package com.subcherry.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.SVNLogEntryPath;

import com.subcherry.Configuration;

public class PathParser {

	private final Pattern _branchPattern;

	public PathParser(Configuration config) {
		_branchPattern = Pattern.compile("^(?:" + config.getBranchPattern() + ")");
	}

	public Path parsePath(SVNLogEntryPath pathEntry) {
		Path result = parsePath(pathEntry.getPath());
		result.setPathEntry(pathEntry);
		String copyPath = pathEntry.getCopyPath();
		if (copyPath != null) {
			result.setCopyPath(parsePath(copyPath));
		}
		return result;
	}

	public Path parsePath(String pathName) {
		int moduleStartIndex = getModuleStartIndex(pathName);
		String branch = getBranch(pathName, moduleStartIndex);
		String resource = getResource(pathName, moduleStartIndex);
		String moduleName = getModule(resource);

		return new Path(pathName, branch, moduleName, resource, null);
	}

	private int getModuleStartIndex(String changedPath) {
		Matcher matcher = _branchPattern.matcher(changedPath);
		boolean matches = matcher.lookingAt();
		if (matches) {
			return matcher.end();
		} else {
			return -1;
		}
	}

	private static String getResource(String changedPath, int moduleStartIndex) {
		return changedPath.substring(moduleStartIndex);
	}

	private String getBranch(String changedPath) {
		return getBranch(changedPath, getModuleStartIndex(changedPath));
	}

	private static String getBranch(String changedPath, int moduleStartIndex) {
		return changedPath.substring(0, moduleStartIndex);
	}

	public static String getModule(String resource) {
		int moduleEndIndex = resource.indexOf(Utils.SVN_SERVER_PATH_SEPARATOR);
		if (moduleEndIndex < 0) {
			return resource;
		} else {
			return resource.substring(0, moduleEndIndex);
		}
	}

}
