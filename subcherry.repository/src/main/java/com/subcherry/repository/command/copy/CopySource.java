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
package com.subcherry.repository.command.copy;

import java.io.File;

import com.subcherry.repository.core.RepositoryURL;
import com.subcherry.repository.core.Revision;
import com.subcherry.repository.core.Target;

public class CopySource {

	private Target _target;

	private Revision _revision;

	public CopySource(Target target, Revision revision) {
		_target = target;
		_revision = revision;
	}

	public Target getTarget() {
		return _target;
	}

	public Revision getRevision() {
		return _revision;
	}

	public static CopySource create(Target target, Revision revision) {
		return new CopySource(target, revision);
	}

	public static CopySource create(Revision pegRevision, Revision revision, RepositoryURL url) {
		return create(Target.fromURL(url, pegRevision), revision);
	}

	public static CopySource create(Revision pegRevision, Revision revision, File file) {
		return create(Target.fromFile(file, pegRevision), revision);
	}

	@Override
	public String toString() {
		return getTarget() + getRevision().at();
	}

}
