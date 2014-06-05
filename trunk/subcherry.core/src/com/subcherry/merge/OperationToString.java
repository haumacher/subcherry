/*
 * TimeCollect records time you spent on your development work.
 * Copyright (C) 2014 Bernhard Haumacher and others
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
package com.subcherry.merge;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class OperationToString {

	public static String toString(SvnOperation<?> op) {
		if (op instanceof SvnMerge) {
			return toStringMerge((SvnMerge) op);
		}
		if (op instanceof SvnCopy) {
			return toStringCopy((SvnCopy) op);
		}
		if (op instanceof SvnDelete) {
			return toStringDelete((SvnDelete) op);
		}
	
		return op.toString();
	}

	public static String toStringDelete(SvnDelete delete) {
		StringBuilder buffer = new StringBuilder();
		buffer.append("svn delete");
		appendTargets(buffer, delete.getTargets());
		return buffer.toString();
	}

	public static String toStringMerge(SvnMerge merge) {
		StringBuilder buffer = new StringBuilder();
		buffer.append("svn merge");
		appendDepth(buffer, merge.getDepth());
		if (merge.isIgnoreAncestry()) {
			buffer.append(" --ignore-ancestry");
		}
		if (merge.isRecordOnly()) {
			buffer.append(" --record-only");
		}
		OperationToString.toStringRanges(buffer, merge.getRevisionRanges());
		buffer.append(" ");
		buffer.append(merge.getSource());
		OperationToString.appendTargets(buffer, merge.getTargets());
		return buffer.toString();
	}

	public static String toStringCopy(SvnCopy copy) {
		StringBuilder buffer = new StringBuilder();
		buffer.append("svn copy");
		appendDepth(buffer, copy.getDepth());
		appendCopySources(buffer, copy.getSources());
		appendTargets(buffer, copy.getTargets());
		return buffer.toString();
	}

	private static void appendDepth(StringBuilder buffer, SVNDepth depth) {
		if (depth != null && depth != SVNDepth.UNKNOWN) {
			buffer.append(" --depth ");
			buffer.append(depth);
		}
	}

	private static void appendCopySources(StringBuilder buffer, Collection<SvnCopySource> sources) {
		for (SvnCopySource source : sources) {
			buffer.append(" ");
			appendTarget(buffer, source.getSource());
		}
	}

	static void appendTargets(StringBuilder buffer, Collection<SvnTarget> targets) {
		for (SvnTarget target : targets) {
			buffer.append(" ");
			appendTarget(buffer, target);
		}
	}

	private static void appendTarget(StringBuilder buffer, SvnTarget target) {
		if (target.getFile() != null && target.getPegRevision() == SVNRevision.UNDEFINED) {
			buffer.append(target.getFile());
		} else {
			buffer.append(target);
		}
	}

	static void toStringRanges(StringBuilder buffer, Collection<SvnRevisionRange> revisionRanges) {
		for (SvnRevisionRange range : revisionRanges) {
			buffer.append(" -r");
			buffer.append(range.getStart());
			buffer.append(":");
			buffer.append(range.getEnd());
		}
	}

}
