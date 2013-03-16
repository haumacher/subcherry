package com.subcherry.configuration.properties;

import java.io.File;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class FileParser implements Parser<File> {

	@Override
	public File parse(String input) {
		return new File(input);
	}

}

