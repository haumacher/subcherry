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

import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class OperationToString {

	static String toStringMerge(SvnMerge merge) {
		StringBuilder buffer = new StringBuilder();
		buffer.append("svn merge");
		if (merge.isIgnoreAncestry()) {
			buffer.append(" --ignore-ancestry ");
		}
		OperationToString.toStringRanges(buffer, merge.getRevisionRanges());
		buffer.append(" ");
		buffer.append(merge.getSource());
		OperationToString.toStringTargets(buffer, merge.getTargets());
		return buffer.toString();
	}

	static void toStringTargets(StringBuilder buffer, Collection<SvnTarget> targets) {
		for (SvnTarget target : targets) {
			buffer.append(" ");
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
