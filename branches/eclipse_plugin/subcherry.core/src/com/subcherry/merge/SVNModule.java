package com.subcherry.merge;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class SVNModule {

	private String moduleName;
	private SVNURL url;

	public SVNModule(String moduleName, String urlPrefix) throws SVNException {
		this.moduleName = moduleName;
		this.url = SVNURL.parseURIDecoded(urlPrefix + moduleName);
	}

	public SVNURL getURL() {
		return url;
	}

	public String getName() {
		return moduleName;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (! (obj instanceof SVNModule)) {
			return false;
		}

		return equalsModule((SVNModule)obj);
	}

	private boolean equalsModule(SVNModule other) {
		return moduleName.equals(other.moduleName);
	}
	
	@Override
	public int hashCode() {
		return moduleName.hashCode();
	}

}

