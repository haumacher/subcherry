package com.subcherry.commit;

import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

/**
 * @version    $Revision$  $Author$  $Date$
 */
public class CommitContext {
	
	public final SVNUpdateClient updateClient;
	public final SVNCommitClient commitClient;

	public CommitContext(SVNUpdateClient updateClient, SVNCommitClient commitClient) {
		this.updateClient = updateClient;
		this.commitClient = commitClient;
	}
}
