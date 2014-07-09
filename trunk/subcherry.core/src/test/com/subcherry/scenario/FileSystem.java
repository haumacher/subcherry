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
package test.com.subcherry.scenario;

import java.io.IOException;

import org.tmatesoft.svn.core.SVNException;

public abstract class FileSystem {

	/**
	 * Creates a new directory in this {@link FileSystem}.
	 * 
	 * @param path
	 *        Path of the new directory.
	 * @return The commit number of the operation, if the {@link FileSystem} is version-controlled
	 *         or <tt>-1</tt> if not.
	 */
	public abstract long mkdir(String path) throws SVNException;

	/**
	 * Creates a new file in this {@link FileSystem}.
	 * 
	 * @param path
	 *        Path of the new file.
	 * @return The commit number of the operation, if the {@link FileSystem} is version-controlled
	 *         or <tt>-1</tt> if not.
	 */
	public abstract long file(String path) throws SVNException, IOException;

	/**
	 * Creates a copy of the resource found under <tt>fromPath</tt> at <tt>toPath</tt>.
	 * 
	 * @param toPath
	 *        Path of the new copy.
	 * @param fromPath
	 *        Path of the resource to copy.
	 * @return The commit number of the operation, if the {@link FileSystem} is version-controlled
	 *         or <tt>-1</tt> if not.
	 */
	public abstract long copy(String toPath, String fromPath) throws SVNException;

	/**
	 * Creates a copy of the resource found under <tt>fromPath</tt> in the given revision at
	 * <tt>toPath</tt>.
	 * 
	 * @param toPath
	 *        Path of the new copy.
	 * @param fromPath
	 *        Path of the resource to copy.
	 * @param revision
	 *        The revision of the source resource to copy
	 * @return The commit number of the operation, if the {@link FileSystem} is version-controlled
	 *         or <tt>-1</tt> if not.
	 */
	public abstract long copy(String toPath, String fromPath, long revision) throws SVNException;
}
