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

public interface BranchConfig {

	/**
	 * @return The name pattern matching the start part of an svn effected path that matches the
	 *         branch of the path.
	 */
	String getBranchPattern();

	/**
	 * Setter for {@link #getBranchPattern()}.
	 */
	void setBranchPattern(String value);

	/**
	 * @return The name pattern matching the special branch "trunk" in the SVN system.
	 */
	String getTrunkPattern();

	/**
	 * Setter for {@link #getTrunkPattern()}.
	 */
	void setTrunkPattern(String value);

}
