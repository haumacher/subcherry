package com.subcherry.configuration.properties;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class LongParser implements Parser<Long> {

	@Override
	public Long parse(String input) {
		if (input == null) {
			return null;
		}
		return Long.parseLong(input);
	}

}

