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
package com.subcherry.merge;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public final class SvnDelete extends SvnOperation<Void> {

	public SvnDelete(SvnOperationFactory factory) {
		super(factory);
	}

	public void setSingleTargetFile(File targetFile) {
		setSingleTarget(SvnTarget.fromFile(targetFile));
	}

	@Override
	public void setSingleTarget(SvnTarget target) {
		checkLocalTarget(target);
		super.setSingleTarget(target);
	}

	@Override
	public void addTarget(SvnTarget target) {
		checkLocalTarget(target);
		super.addTarget(target);
	}

	private void checkLocalTarget(SvnTarget target) {
		if (target.getFile() == null) {
			throw new IllegalArgumentException("Only local files can be deleted locally.");
		}
	}

	@Override
	public Void run() throws SVNException {
		for (SvnTarget target : getTargets()) {
			File file = target.getFile();
			file.delete();
		}
		return null;
	}
}