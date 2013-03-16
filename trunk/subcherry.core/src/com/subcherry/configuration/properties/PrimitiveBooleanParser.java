package com.subcherry.configuration.properties;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class PrimitiveBooleanParser implements Parser<Boolean> {

	@Override
	public Boolean parse(String input) {
		if (input == null) {
			return false;
		}
		return Boolean.valueOf(input);
	}

}

