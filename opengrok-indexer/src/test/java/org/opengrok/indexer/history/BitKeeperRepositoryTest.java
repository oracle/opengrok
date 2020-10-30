/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.TreeSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.util.TestRepository;

/**
 * Tests for BitKeeperRepository.
 * @author James Service &lt;jas2701@googlemail.com&gt;
 */
@ConditionalRun(RepositoryInstalled.BitKeeperInstalled.class)
public class BitKeeperRepositoryTest {

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    private TestRepository testRepo;
    private BitKeeperRepository bkRepo;
    private List<String> bkFiles;

    private static class BitKeeperFilenameFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return !(name.equals("BitKeeper") || name.equals(".bk"));
        }
    }

    @Before
    public void setUp() {
        try {
            testRepo = new TestRepository();
            testRepo.create(getClass().getResourceAsStream("repositories.zip"));
            final File root = new File(testRepo.getSourceRoot(), "bitkeeper");
            bkRepo = (BitKeeperRepository) RepositoryFactory.getRepository(root);
            bkFiles = Arrays.asList(root.list(new BitKeeperFilenameFilter()));
        } catch (final Exception e) {
            testRepo = null;
            bkRepo = null;
            bkFiles = null;
        }
    }

    @After
    public void tearDown() {
        if (testRepo != null) {
            testRepo.destroy();
            testRepo = null;
        }
        bkRepo = null;
        bkFiles = null;
    }

    private static void validateHistory(History history) throws Exception {
        final List<HistoryEntry> entries = history.getHistoryEntries();
        final List<String> renames = history.getRenamedFiles();

        assertTrue("File history has no entries.", entries.size() > 0);

        // Since we are not supporting directory histories
        // each entry must have only one file in its list.
        for (final HistoryEntry entry : entries) {
            assertNotNull("File history has missing revision.", entry.getRevision());
            assertNotNull("File history has missing author.", entry.getAuthor());
            assertNotNull("File history has missing date.", entry.getDate());
            assertNotNull("File history has missing message.", entry.getMessage());
            assertEquals("File history has invalid file list.", entry.getFiles().size(), 1);
        }

        // Validate that the renamed files list corresponds
        // to all the file names we know of for this file.
        final TreeSet<String> fileNames = new TreeSet<>();
        for (final HistoryEntry entry : entries) {
            fileNames.addAll(entry.getFiles());
        }
        final String currentName = entries.get(0).getFiles().first();
        final TreeSet<String> pastNames = new TreeSet<>(renames);
        pastNames.add(currentName);
        assertEquals("File history has incorrect rename list.", fileNames, pastNames);
    }

    @Test
    public void testGetHistory() throws Exception {
        assertNotNull("Couldn't read bitkeeper test repository.", bkRepo);

        for (final String bkFile : bkFiles) {
            final File file = new File(bkRepo.getDirectoryName(), bkFile);
            final History fullHistory = bkRepo.getHistory(file);
            final History partHistory = bkRepo.getHistory(file, "1.2");
            // I made sure that each file had a 1.2

            validateHistory(fullHistory);
            validateHistory(partHistory);

            // Passing 1.2 to get History should remove 1.1 and 1.2
            // revisions from each file, so check number of entries.
            assertEquals("Partial file history is wrong size", fullHistory.getHistoryEntries().size(),
                    (partHistory.getHistoryEntries().size() + 2));
        }
    }

    @Test
    public void testGetHistoryInvalid() throws Exception {
        assertNotNull("Couldn't read bitkeeper test repository.", bkRepo);

        final File file = new File(bkRepo.getDirectoryName(), "fakename.cpp");

        boolean caughtFull = false;
        try {
            final History fullHistory = bkRepo.getHistory(file);
        } catch (final HistoryException e) {
            caughtFull = true;
        }
        assertTrue("No exception thrown by getHistory with fake file", caughtFull);

        boolean caughtPart = false;
        try {
            final History partHistory = bkRepo.getHistory(file, "1.2");
        } catch (final HistoryException e) {
            caughtPart = true;
        }
        assertTrue("No exception thrown by getHistory with fake file", caughtPart);
    }

    private static String readStream(InputStream stream) throws IOException {
        final Scanner scanner = new Scanner(stream).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    @Test
    public void testGetHistoryGet() throws Exception {
        assertNotNull("Couldn't read bitkeeper test repository.", bkRepo);

        for (final String bkFile : bkFiles) {
            final String currentVersion = readStream(bkRepo.getHistoryGet(bkRepo.getDirectoryName(), bkFile, "+"));
            final String firstVersion = readStream(bkRepo.getHistoryGet(bkRepo.getDirectoryName(), bkFile, "1.1"));

            assertNotNull("Nothing returned by getHistoryGet.", currentVersion);
            assertNotNull("Nothing returned by getHistoryGet.", firstVersion);
            assertThat("Files returned by getHistoryGet are incorrect.", currentVersion, not(equalTo(firstVersion)));
        }
    }

    @Test
    public void testGetHistoryGetInvalid() throws Exception {
        assertNotNull("Couldn't read bitkeeper test repository.", bkRepo);

        assertNull("Something returned by getHistoryGet with fake file",
                bkRepo.getHistoryGet(bkRepo.getDirectoryName(), "fakename.cpp", "+"));
        assertNull("Something returned by getHistoryGet with fake file",
                bkRepo.getHistoryGet(bkRepo.getDirectoryName(), "fakename.cpp", "1.1"));
    }

    @Test
    public void testAnnotation() throws Exception {
        assertNotNull("Couldn't read bitkeeper test repository.", bkRepo);

        for (final String bkFile : bkFiles) {
            final File file = new File(bkRepo.getDirectoryName(), bkFile);
            final Annotation currentVersion = bkRepo.annotate(file, "+");
            final Annotation firstVersion = bkRepo.annotate(file, "1.1");

            assertEquals("Wrong file returned by annotate.", currentVersion.getFilename(), file.getName());
            assertEquals("Wrong file returned by annotate.", firstVersion.getFilename(), file.getName());
            assertTrue("Incorrect revisions returned by annotate.", currentVersion.getRevisions().size() > 1);
            assertEquals("Incorrect revisions returned by annotate.", 1, firstVersion.getRevisions().size());
        }
    }

    @Test
    public void testAnnotationInvalid() throws Exception {
        assertNotNull("Couldn't read bitkeeper test repository.", bkRepo);

        final File file = new File(bkRepo.getDirectoryName(), "fakename.cpp");

        boolean caughtCurrent = false;
        try {
            final Annotation currentVersion = bkRepo.annotate(file, "+");
        } catch (final IOException e) {
            caughtCurrent = true;
        }
        assertTrue("No exception thrown by annotate with fake file", caughtCurrent);

        boolean caughtFirst = false;
        try {
            final Annotation firstVersion = bkRepo.annotate(file, "1.1");
        } catch (final IOException e) {
            caughtFirst = true;
        }
        assertTrue("No exception thrown by annotate with fake file", caughtFirst);
    }
}
