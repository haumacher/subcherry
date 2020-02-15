/*
 * SubCherry - Cherry Picking with Trac and Subversion
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
package com.subcherry.repository.impl;

import com.subcherry.repository.command.CommandVisitor;
import com.subcherry.repository.command.OperationFactory;
import com.subcherry.repository.command.copy.Copy;
import com.subcherry.repository.command.copy.CopySource;
import com.subcherry.repository.core.Revision;

public class DefaultCopy extends DefaultTargetDepthCommand implements Copy {

	private boolean _makeParents;

	private boolean _failWhenDstExists;

	private boolean _move;

	private Revision _revision;

	private CopySource _copySource;

	public DefaultCopy(OperationFactory factory) {
		super(factory);
	}

	@Override
	public boolean getMakeParents() {
		return _makeParents;
	}

	@Override
	public void setMakeParents(boolean makeParents) {
		_makeParents = makeParents;
	}

	@Override
	public boolean getFailWhenDstExists() {
		return _failWhenDstExists;
	}

	@Override
	public void setFailWhenDstExists(boolean failWhenDstExists) {
		_failWhenDstExists = failWhenDstExists;

	}

	@Override
	public boolean getMove() {
		return _move;
	}

	@Override
	public void setMove(boolean move) {
		_move = move;
	}

	@Override
	public CopySource getCopySource() {
		return _copySource;
	}

	@Override
	public void setCopySource(CopySource copySource) {
		_copySource = copySource;
	}

	@Override
	public Revision getRevision() {
		return _revision;
	}

	@Override
	public void setRevision(Revision revision) {
		_revision = revision;
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append("svn cp");
		if (getMakeParents()) {
			result.append(" --parents");
		}
		if (getMove()) {
			result.append(" --move");
		}

		result.append(" ");
		result.append(getCopySource());

		result.append(" ");
		result.append(getTarget());
		return result.toString();
	}

	@Override
	public <R, A> R visit(CommandVisitor<R, A> v, A arg) {
		return v.visitCopy(this, arg);
	}

}
