package com.subcherry;

import org.apache.commons.codec.binary.Base64;

import com.subcherry.configuration.annotation.NoValueCaching;
import com.subcherry.configuration.annotation.ValueParser;
import com.subcherry.configuration.properties.Parser;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public interface LoginCredential {
	
	public static class PasswordDecoder implements Parser<String>{

		@Override
		public String parse(String input) {
			return new String(Base64.decodeBase64(input));
		}
		
	}
	
	String getUser();
	
	@ValueParser(PasswordDecoder.class)
	@NoValueCaching
	String getPasswd();

}

