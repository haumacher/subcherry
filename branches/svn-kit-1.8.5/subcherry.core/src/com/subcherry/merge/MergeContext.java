package com.subcherry.merge;

import org.tmatesoft.svn.core.wc.SVNDiffClient;

/**
 * @version    $Revision$  $Author$  $Date$
 */
public class MergeContext {
	
	public final SVNDiffClient diffClient;

	public MergeContext(SVNDiffClient diffClient) {
		this.diffClient = diffClient;
	}
}
