/*
 * TimeCollect records time you spent on your development work.
 * Copyright (C) 2015 Bernhard Haumacher and others
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
package com.subcherry.repository.command.copy;

import com.subcherry.repository.command.TargetCommand;
import com.subcherry.repository.core.Revision;

public interface Copy extends TargetCommand {

	boolean getMakeParents();

	void setMakeParents(boolean value);

	boolean getFailWhenDstExists();

	void setFailWhenDstExists(boolean value);

	boolean getMove();

	void setMove(boolean value);

	CopySource getCopySource();

	void setCopySource(CopySource copySource);

	Revision getRevision();

	void setRevision(Revision revision);

}
