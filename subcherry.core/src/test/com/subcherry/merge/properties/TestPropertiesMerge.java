/*
 * SubCherry - Cherry Picking with Trac and Subversion
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
package test.com.subcherry.merge.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import com.subcherry.repository.command.status.StatusType;
import com.subcherry.repository.core.RepositoryException;
import com.subcherry.repository.merge.properties.PropertiesMerge;

import junit.framework.TestCase;

/**
 * Test case for {@link PropertiesMerge}.
 * 
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 * @version $Revision$ $Author$ $Date$
 */
public class TestPropertiesMerge extends TestCase {

	private static final String BASE_DIR = "test/fixtures/merge/properties/";

	public void test01MergeConflictFree() throws IOException, RepositoryException {
		doMerge("01-no-conflict", StatusType.MERGED);
	}

	public void test02MergeConflictAddAdd() throws IOException, RepositoryException {
		doMerge("02-conflict-add-add", StatusType.CONFLICTED);
	}

	public void test03MergeConflictEditEdit() throws IOException, RepositoryException {
		doMerge("03-conflict-edit-edit", StatusType.CONFLICTED);
	}

	public void test04MergeNoConflictDeleteDeleted() throws IOException, RepositoryException {
		doMerge("04-no-conflict-delete-deleted", StatusType.MERGED);
	}

	public void test05MergeNoConflictAddAfterDeleted() throws IOException, RepositoryException {
		doMerge("05-no-conflict-add-after-deleted", StatusType.MERGED);
	}

	public void test06MergeNoConflictAddAtBeginning() throws IOException, RepositoryException {
		doMerge("06-no-conflict-add-at-beginning", StatusType.MERGED);
	}

	public void test07MergeNoConflictAddBelowEmptyLine() throws IOException, RepositoryException {
		doMerge("07-no-conflict-add-below-empty-line", StatusType.MERGED);
	}

	public void test08MergeNoConflictAddAboveEmptyLine() throws IOException, RepositoryException {
		doMerge("08-no-conflict-add-above-empty-line", StatusType.MERGED);
	}

	public void test09MergeNoConflictAddWithEmptyLineAbove() throws IOException, RepositoryException {
		doMerge("09-no-conflict-add-with-empty-line-above", StatusType.MERGED);
	}

	public void test10PreserveTrailingSpace() throws IOException, RepositoryException {
		doMerge("10-preserve-trailing-space", StatusType.MERGED);
	}

	private void doMerge(String name, StatusType expectedResult) throws RepositoryException, FileNotFoundException,
			IOException {
		PropertiesMerge merge = new PropertiesMerge();
		File tmp = FileTestUtil.tmp();

		File base = new File(BASE_DIR + name + "/base.properties");
		File local = new File(BASE_DIR + name + "/local.properties");
		File latest = new File(BASE_DIR + name + "/latest.properties");
		File result = new File(tmp, "result-" + name + ".properties");
		result.delete();

		StatusType status = merge.merge(base, local, latest, null, result);
		assertEquals(expectedResult, status);

		File expected = new File(BASE_DIR + name + "/expected.properties");
		if (expected.exists()) {
			Properties expectedProperties = load(expected);
			Properties resultProperties = load(result);

			assertEquals("Unexpected '" + result.getName() + "'.", expectedProperties, resultProperties);
		}

		String expectedContents = FileTestUtil.loadISO_8859_1(expected);
		String resultContents = FileTestUtil.loadISO_8859_1(result);
		assertEquals(expectedContents, resultContents);
	}

	private Properties load(File result) throws FileNotFoundException, IOException {
		FileInputStream in = new FileInputStream(result);
		Properties properties = new Properties();
		properties.load(in);
		return properties;
	}

}
