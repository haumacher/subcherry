package com.subcherry.merge;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

/**
 * @version    $Revision$  $Author$  $Date$
 */
public class Merge {
	
	private final Collection<SvnOperation<?>> _operations;

	private final long _revision;

	public Merge(long revision, Collection<SvnOperation<?>> operations) {
		_revision = revision;
		_operations = operations;
	}

	/**
	 * The single revision to merge.
	 */
	public long getRevision() {
		return _revision;
	}

	/**
	 * The resources for which a merge is required.
	 */
	public Collection<SvnOperation<?>> getOperations() {
		return _operations;
	}

	/**
	 * Whether this is a no-op.
	 */
	public boolean isEmpty() {
		return getOperations().isEmpty();
	}

	public Map<File, List<SVNConflictDescription>> run(MergeContext context) throws SVNException {
		Map<File, List<SVNConflictDescription>> allConflicts = Collections.emptyMap();
		for (SvnOperation<?> operation : this.getOperations()) {
			Map<File, List<SVNConflictDescription>> moduleConflicts = execute(operation);

			allConflicts = addAll(allConflicts, moduleConflicts);
		}
		return allConflicts;
	}

	private Map<File, List<SVNConflictDescription>> execute(SvnOperation<?> op) throws SVNException {
		System.out.println(OperationToString.toString(op));

		SvnOperationFactory operationFactory = op.getOperationFactory();

		TouchCollector touchedFilesHandler = new TouchCollector(operationFactory.getEventHandler());
		operationFactory.setEventHandler(touchedFilesHandler);
		try {
			MergeConflictCollector conclictCollector =
				new MergeConflictCollector(
					operationFactory.getOperationHandler(),
					touchedFilesHandler.getTouchedFiles());
			operationFactory.setOperationHandler(conclictCollector);
			try {
				op.run();
				return conclictCollector.getMergeConflicts();
			} finally {
				operationFactory.setOperationHandler(conclictCollector.getDelegate());
			}
		} finally {
			operationFactory.setEventHandler(touchedFilesHandler.getDelegate());
		}
	}

	private static <K, V> Map<K, V> addAll(Map<K, V> allConflicts, Map<K, V> moduleConflicts) {
		if (allConflicts.isEmpty()) {
			// allConflicts is unmodifiable.
			allConflicts = moduleConflicts;
		} else {
			allConflicts.putAll(moduleConflicts);
		}
		return allConflicts;
	}

}
