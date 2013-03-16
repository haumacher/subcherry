package com.subcherry.configuration.properties;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class PrimitiveIntegerParser implements Parser<Integer> {

	@Override
	public Integer parse(String input) {
		if (input == null) {
			return 0;
		}
		return Integer.parseInt(input);
	}

}

