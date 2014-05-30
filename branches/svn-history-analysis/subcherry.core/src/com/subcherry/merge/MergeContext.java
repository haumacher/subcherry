package com.subcherry.merge;

import org.tmatesoft.svn.core.wc.SVNDiffClient;

import com.subcherry.Configuration;

/**
 * @version    $Revision$  $Author$  $Date$
 */
public class MergeContext {
	
	public final SVNDiffClient diffClient;
	public final Configuration config;

	public MergeContext(SVNDiffClient diffClient, Configuration config) {
		this.diffClient = diffClient;
		this.config = config;
	}
}
