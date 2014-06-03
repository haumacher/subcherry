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

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.haumacher.common.config.ObjectParser;

public class AdditionalRevision {

	public static class Parser extends ObjectParser<Map<Long, AdditionalRevision>> {

		private static final Pattern FORMAT =
			Pattern.compile("\\s*(" + "\\d+" + ")" + "\\s*" +
				"(?:" + "\\(" + "\\s*" + "([^\\)]+)" + "\\s*" + "\\)" + ")?" + "(\\s*,)?\\s*");

		@Override
		public Map<Long, AdditionalRevision> defaultValue() {
			return Collections.emptyMap();
		}

		@Override
		public Map<Long, AdditionalRevision> parse(String text) {
			HashMap<Long, AdditionalRevision> result = new HashMap<>();
			Matcher matcher = FORMAT.matcher(text);
			while (matcher.lookingAt()) {
				long revision = Long.parseLong(matcher.group(1));
				String pathsSrc = matcher.group(2);

				Set<String> paths = null;
				if (pathsSrc != null) {
					String[] pathNames = pathsSrc.split("\\s*" + File.pathSeparatorChar + "\\s*");
					if (pathNames != null && pathNames.length > 0) {
						paths = new HashSet<>();
						for (String path : pathNames) {
							paths.add(path.trim());
						}
					}
				}
				matcher.region(matcher.end(), text.length());

				result.put(revision, new AdditionalRevision(revision, paths));
			}

			if (matcher.regionStart() < text.length()) {
				throw new RuntimeException("Cannot parse additional revisions: '"
					+ text.substring(matcher.regionStart()) + "'");
			}

			return result;
		}

		@Override
		public String unparse(Map<Long, AdditionalRevision> value) {
			StringBuilder buffer = new StringBuilder();
			boolean firstRev = true;
			for (AdditionalRevision additional : value.values()) {
				if (firstRev) {
					firstRev = false;
				} else {
					buffer.append(',');
				}
				buffer.append(additional.getRevision());
				if (additional.getIncludePaths() != null) {
					buffer.append('(');
					boolean firstPath = true;
					for (String path : additional.getIncludePaths()) {
						if (firstPath) {
							firstPath = false;
						} else {
							buffer.append(File.pathSeparator);
						}
						buffer.append(path);
					}
					buffer.append(')');
				}
			}
			return buffer.toString();
		}

	}

	private final long revision;

	private final Set<String> includePaths;

	public AdditionalRevision(long revision, Set<String> includePaths) {
		this.revision = revision;
		this.includePaths = includePaths;
	}

	public long getRevision() {
		return revision;
	}

	public Set<String> getIncludePaths() {
		return includePaths;
	}

}
