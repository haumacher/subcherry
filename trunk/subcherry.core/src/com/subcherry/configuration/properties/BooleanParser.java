package com.subcherry.configuration.properties;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class BooleanParser implements Parser<Boolean> {

	@Override
	public Boolean parse(String input) {
		if (input == null) {
			return null;
		}
		return Boolean.valueOf(input);
	}

}

