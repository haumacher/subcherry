package com.subcherry.merge;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

/**
 * Description of a single native merge operation.
 * 
 * @version $Revision$ $Author$ $Date: 2013-03-16 15:55:27 +0100
 *          (Sat, 16 Mar 2013) $
 */
public class MergeResource {

	private String moduleName;
	private SVNURL url;

	private final boolean _ignoreAncestry;

	/**
	 * Creates a {@link MergeResource}.
	 * 
	 * @param moduleName
	 *        See {@link #getName()}.
	 * @param urlPrefix
	 *        The SVN url including the source branch.
	 * @param ignoreAncestry
	 *        See {@link #getIgnoreAncestry()}.
	 */
	public MergeResource(String moduleName, String urlPrefix, boolean ignoreAncestry) throws SVNException {
		this.moduleName = moduleName;
		this.url = SVNURL.parseURIDecoded(urlPrefix + moduleName);
		_ignoreAncestry = ignoreAncestry;
	}

	/**
	 * The source of the merge.
	 */
	public SVNURL getURL() {
		return url;
	}

	/**
	 * The workspace-relative name of the target of the merge.
	 */
	public String getName() {
		return moduleName;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (! (obj instanceof MergeResource)) {
			return false;
		}

		return equalsModule((MergeResource)obj);
	}

	private boolean equalsModule(MergeResource other) {
		return moduleName.equals(other.moduleName);
	}
	
	@Override
	public int hashCode() {
		return moduleName.hashCode();
	}

	/**
	 * Whether SVN merge info should be ignored during this merge.
	 */
	public boolean getIgnoreAncestry() {
		return _ignoreAncestry;
	}

}

