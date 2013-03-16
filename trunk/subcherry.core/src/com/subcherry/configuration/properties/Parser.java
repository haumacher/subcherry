package com.subcherry.configuration.properties;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public interface Parser<T> {
	
	T parse(String input);

}

