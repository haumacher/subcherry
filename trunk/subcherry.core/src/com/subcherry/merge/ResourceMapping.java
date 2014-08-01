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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ResourceMapping {

	public static ResourceMapping create(Map<String, String> patternReplacements) {
		if (patternReplacements != null && !patternReplacements.isEmpty()) {
			return new RegexpResourceMapping(patternReplacements);
		} else {
			return NoMapping.INSTANCE;
		}
	}

	public abstract String map(String resource);

	private static class RegexpResourceMapping extends ResourceMapping {

		private List<Replacer> _replacers = new ArrayList<>();

		public RegexpResourceMapping(Map<String, String> patternReplacements) {
			for (Entry<String, String> entry : patternReplacements.entrySet()) {
				String key = entry.getKey();

				_replacers.add(new Replacer(Pattern.compile(key), entry.getValue()));
			}
		}

		@Override
		public String map(String resource) {
			for (int n = 0, cnt = _replacers.size(); n < cnt; n++) {
				String replacement = _replacers.get(n).replace(resource);
				if (replacement != null) {
					return replacement;
				}
			}

			return null;
		}

		private static final class Replacer {
			private Pattern _pattern;

			private String _replacement;

			public Replacer(Pattern pattern, String replacement) {
				_pattern = pattern;
				_replacement = replacement;
			}

			public String replace(String resource) {
				Matcher matcher = _pattern.matcher(resource);
				if (!matcher.lookingAt()) {
					return null;
				}
				return matcher.replaceFirst(_replacement);
			}

		}

	}

	private static class NoMapping extends ResourceMapping {

		/**
		 * Singleton {@link ResourceMapping.NoMapping} instance.
		 */
		public static final NoMapping INSTANCE = new NoMapping();

		private NoMapping() {
			// Singleton constructor.
		}

		@Override
		public String map(String resource) {
			return null;
		}
	}

}
