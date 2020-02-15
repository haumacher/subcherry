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
package com.subcherry.repository.core;

import java.io.File;

public abstract class Target {

	public enum Kind {
		FILE, URL;
	}

	private Revision _pegRevision;

	Target(Revision pegRevision) {
		_pegRevision = pegRevision;
	}

	public abstract Kind kind();

	public Revision getPegRevision() {
		return _pegRevision;
	}

	public static Target fromFile(File targetFile) {
		return fromFile(targetFile, Revision.UNDEFINED);
	}

	public static Target fromFile(File file, Revision pegRevision) {
		return new FileTarget(file, pegRevision);
	}

	public static Target fromURL(RepositoryURL url) {
		return fromURL(url, Revision.HEAD);
	}

	public static Target fromURL(RepositoryURL url, Revision pegRevision) {
		return new UrlTarget(url, pegRevision);
	}

	public static class FileTarget extends Target {

		private File _file;

		FileTarget(File file, Revision pegRevision) {
			super(pegRevision);
			_file = file;
		}

		public File getFile() {
			return _file;
		}

		@Override
		public Kind kind() {
			return Kind.FILE;
		}

		@Override
		public String toString() {
			return getFile() + getPegRevision().at();
		}

	}

	public static class UrlTarget extends Target {

		private RepositoryURL _url;

		UrlTarget(RepositoryURL url, Revision pegRevision) {
			super(pegRevision);
			_url = url;
		}

		public RepositoryURL getUrl() {
			return _url;
		}

		@Override
		public Kind kind() {
			return Kind.URL;
		}

		@Override
		public String toString() {
			return getUrl() + getPegRevision().at();
		}

	}

}
