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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import de.haumacher.common.config.ObjectParser;
import de.haumacher.common.config.Parser;

/**
 * {@link Parser} for mapping of {@link String} to {@link String}.
 * 
 * <p>
 * The syntax of the mapping is expected to be: <code>key -> value, key -> value</code>
 * </p>
 * 
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 * @version $Revision$ $Author$ $Date$
 */
public class MappingParser extends ObjectParser<Map<String, String>> {

	@Override
	public Map<String, String> init() {
		return new HashMap<>();
	}

	@Override
	public Map<String, String> parse(String text) {
		Map<String, String> result = new LinkedHashMap<>();
		for (String entry : text.split("\\s*,\\s*")) {
			String[] keyValue = entry.split("\\s*\\-\\>\\s*");
			if (keyValue.length != 2) {
				throw new IllegalArgumentException("Invalid mapping specification: " + entry);
			}
			result.put(keyValue[0].trim(), keyValue[1].trim());
		}
		return result;
	}

	@Override
	public String unparse(Map<String, String> value) {
		StringBuilder buffer = new StringBuilder();
		for (Entry<String, String> entry : value.entrySet()) {
			if (buffer.length() > 0) {
				buffer.append(", ");
			}
			buffer.append(entry.getKey());
			buffer.append(" -> ");
			buffer.append(entry.getValue());
		}
		return buffer.toString();
	}

}
