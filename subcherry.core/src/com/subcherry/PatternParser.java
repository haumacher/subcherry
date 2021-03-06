/*
 * SubCherry - Cherry Picking with Trac and Subversion
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

import java.util.regex.Pattern;

import de.haumacher.common.config.ObjectParser;

public class PatternParser extends ObjectParser<Pattern> {

	@Override
	public Pattern parse(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		return Pattern.compile(text);
	}

	@Override
	public String unparse(Pattern value) {
		if (value == null) {
			return "";
		}
		return value.pattern();
	}

}
