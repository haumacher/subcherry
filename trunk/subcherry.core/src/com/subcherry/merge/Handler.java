package com.subcherry.merge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.subcherry.Configuration;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class Handler {

	protected final Configuration _config;
	protected final Pattern _branchPattern;

	public Handler(Configuration config) {
		_config = config;
		_branchPattern = Pattern.compile("^(?:" + config.getBranchPattern() + ")");
	}

	protected int getModuleStartIndex(String changedPath) {
		Matcher matcher = _branchPattern.matcher(changedPath);
		boolean matches = matcher.lookingAt();
		if (matches) {
			return matcher.end();
		} else {
			return -1;
		}
	}

}
