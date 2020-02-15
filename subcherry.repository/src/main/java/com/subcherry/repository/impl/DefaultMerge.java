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
package com.subcherry.repository.impl;

import com.subcherry.repository.command.CommandVisitor;
import com.subcherry.repository.command.OperationFactory;
import com.subcherry.repository.command.diff.DiffOptions;
import com.subcherry.repository.command.merge.Merge;
import com.subcherry.repository.core.RevisionRange;
import com.subcherry.repository.core.Target;

public class DefaultMerge extends DefaultTargetDepthCommand implements Merge {

	private boolean _recordOnly;

	private RevisionRange _range;

	private boolean _ignoreAnchestry;

	private DiffOptions _mergeOptions;

	private Target _source;

	private boolean _reintegrate;

	private boolean _allowMixedRevisions;

	public DefaultMerge(OperationFactory factory) {
		super(factory);
	}

	@Override
	public boolean getRecordOnly() {
		return _recordOnly;
	}

	@Override
	public void setRecordOnly(boolean recordOnly) {
		_recordOnly = recordOnly;
	}

	@Override
	public boolean getAllowMixedRevisions() {
		return _allowMixedRevisions;
	}

	@Override
	public void setAllowMixedRevisions(boolean allowMixedRevisions) {
		_allowMixedRevisions = allowMixedRevisions;
	}

	@Override
	public RevisionRange getRevisionRange() {
		return _range;
	}

	@Override
	public void addRevisionRange(RevisionRange range) {
		_range = range;
	}

	@Override
	public boolean getIgnoreAnchestry() {
		return _ignoreAnchestry;
	}

	@Override
	public void setIgnoreAncestry(boolean ignoreAnchestry) {
		_ignoreAnchestry = ignoreAnchestry;
	}

	@Override
	public DiffOptions getMergeOptions() {
		return _mergeOptions;
	}

	@Override
	public void setMergeOptions(DiffOptions mergeOptions) {
		_mergeOptions = mergeOptions;
	}

	@Override
	public Target getSource() {
		return _source;
	}

	@Override
	public void setSource(Target source) {
		_source = source;
	}

	@Override
	public boolean getReintegrate() {
		return _reintegrate;
	}

	@Override
	public void setReintegrate(boolean reintegrate) {
		_reintegrate = reintegrate;
	}

	@Override
	public void setSource(Target source, boolean reintegrate) {
		setSource(source);
		setReintegrate(reintegrate);
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append("svn merge");
		result.append(" -");
		result.append(getRevisionRange());
		result.append(" --accept postpone");
		if (getAllowMixedRevisions()) {
			result.append(" --allow-mixed-revisions");
		}
		if (getIgnoreAnchestry()) {
			result.append(" --ignore-ancestry");
		}
		if (getRecordOnly()) {
			result.append(" --record-only");
		}
		if (getReintegrate()) {
			result.append(" --reintegrate");
		}
		result.append(" --depth ");
		result.append(getDepth());

		result.append(" ");
		result.append(getSource());
		result.append(" ");
		result.append(getTarget());
		return result.toString();
	}

	@Override
	public <R, A> R visit(CommandVisitor<R, A> v, A arg) {
		return v.visitMerge(this, arg);
	}
}
