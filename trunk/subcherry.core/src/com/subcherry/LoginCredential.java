package com.subcherry;

import org.apache.commons.codec.binary.Base64;

import de.haumacher.common.config.Parser;
import de.haumacher.common.config.Value;
import de.haumacher.common.config.annotate.ValueParser;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public interface LoginCredential extends Value {
	
	public static class PasswordDecoder implements Parser<String>{

		@Override
		public String parse(String input) {
			return new String(Base64.decodeBase64(input));
		}
		
	}
	
	String getUser();
	
	@ValueParser(PasswordDecoder.class)
	String getPasswd();

}

