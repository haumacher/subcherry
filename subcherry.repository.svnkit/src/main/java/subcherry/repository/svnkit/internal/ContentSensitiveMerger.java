/*
 * SubCherry - Cherry Picking with Trac and Subversion
 * Copyright (C) 2015 Bernhard Haumacher and others
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package subcherry.repository.svnkit.internal;

import static subcherry.repository.svnkit.internal.Conversions.*;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.internal.wc.SVNDiffConflictChoiceStyle;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNMergeResult;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.ISvnMerger;
import org.tmatesoft.svn.core.wc2.SvnMergeResult;

import com.subcherry.repository.merge.FileMerge;
import com.subcherry.repository.merge.properties.PropertiesMerge;

public class ContentSensitiveMerger implements ISvnMerger {

	FileMerge _propertiesMerger = new PropertiesMerge();

	public ContentSensitiveMerger(byte[] conflictStart, byte[] conflictSeparator, byte[] conflictEnd,
			ISVNConflictHandler conflictResolver, SVNDiffConflictChoiceStyle chooseModifiedLatest) {
		// Ignore.
	}

	@Override
	public SvnMergeResult mergeText(ISvnMerger baseMerger, File resultFile, File targetAbspath,
			File detranslatedTargetAbspath, File leftAbspath, File rightAbspath, String targetLabel, String leftLabel,
			String rightLabel, SVNDiffOptions options) throws SVNException {
		try {
			if (targetAbspath.getName().endsWith(".properties")) {
				SVNStatusType result =
					unwrap(_propertiesMerger.merge(leftAbspath, detranslatedTargetAbspath, rightAbspath, wrap(options), resultFile));
				return SvnMergeResult.create(result);
			} else {
				return baseMerger.mergeText(baseMerger, resultFile, targetAbspath, detranslatedTargetAbspath, leftAbspath,
					rightAbspath, targetLabel, leftLabel, rightLabel, options);
			}
		} catch (com.subcherry.repository.core.RepositoryException ex) {
			throw Conversions.unwrap(ex);
		}
	}

	@Override
	public SvnMergeResult mergeProperties(ISvnMerger baseMerger, File localAbsPath, SVNNodeKind kind,
			SVNConflictVersion leftVersion, SVNConflictVersion rightVersion, SVNProperties serverBaseProperties,
			SVNProperties pristineProperties, SVNProperties actualProperties, SVNProperties propChanges,
			boolean baseMerge, boolean dryRun) throws SVNException {
		return baseMerger.mergeProperties(baseMerger, localAbsPath, kind, leftVersion, rightVersion,
			serverBaseProperties, pristineProperties, actualProperties, propChanges, baseMerge, dryRun);
	}

	@Override
	public SVNMergeResult mergeText(SVNMergeFileSet files, boolean dryRun, SVNDiffOptions options) throws SVNException {
		throw new UnsupportedOperationException();
	}

	@Override
	public SVNMergeResult mergeProperties(String localPath, SVNProperties workingProperties,
			SVNProperties baseProperties, SVNProperties serverBaseProps, SVNProperties propDiff,
			SVNAdminArea adminArea, SVNLog log, boolean baseMerge, boolean dryRun) throws SVNException {
		throw new UnsupportedOperationException();
	}

}