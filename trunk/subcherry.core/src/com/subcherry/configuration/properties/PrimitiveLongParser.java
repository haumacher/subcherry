package com.subcherry.configuration.properties;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class PrimitiveLongParser implements Parser<Long> {

	@Override
	public Long parse(String input) {
		if (input == null) {
			return 0L;
		}
		return Long.parseLong(input);
	}

}

