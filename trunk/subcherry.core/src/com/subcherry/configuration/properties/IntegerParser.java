package com.subcherry.configuration.properties;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class IntegerParser implements Parser<Integer> {

	@Override
	public Integer parse(String input) {
		if (input == null) {
			return null;
		}
		return Integer.parseInt(input);
	}

}

