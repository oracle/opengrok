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
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.BITKEEPER;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.util.TestRepository;

/**
 * Tests for BitKeeperRepository.
 * @author James Service &lt;jas2701@googlemail.com&gt;
 */
@EnabledForRepository(BITKEEPER)
public class BitKeeperRepositoryTest {

    private TestRepository testRepo;
    private BitKeeperRepository bkRepo;
    private List<String> bkFiles;

    private static class BitKeeperFilenameFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return !(name.equals("BitKeeper") || name.equals(".bk"));
        }
    }

    @BeforeEach
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

    @AfterEach
    public void tearDown() {
        if (testRepo != null) {
            testRepo.destroy();
            testRepo = null;
        }
        bkRepo = null;
        bkFiles = null;
    }

    private static void validateHistory(History history) {
        final List<HistoryEntry> entries = history.getHistoryEntries();
        final Set<String> renames = history.getRenamedFiles();

        assertTrue(entries.size() > 0, "File history has no entries.");

        // Since we are not supporting directory histories
        // each entry must have only one file in its list.
        for (final HistoryEntry entry : entries) {
            assertNotNull(entry.getRevision(), "File history has missing revision.");
            assertNotNull(entry.getAuthor(), "File history has missing author.");
            assertNotNull(entry.getDate(), "File history has missing date.");
            assertNotNull(entry.getMessage(), "File history has missing message.");
            assertEquals(entry.getFiles().size(), 1, "File history has invalid file list.");
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
        assertEquals(fileNames, pastNames, "File history has incorrect rename list.");
    }

    @Test
    public void testGetHistory() throws Exception {
        assertNotNull(bkRepo, "Couldn't read bitkeeper test repository.");

        for (final String bkFile : bkFiles) {
            final File file = new File(bkRepo.getDirectoryName(), bkFile);
            final History fullHistory = bkRepo.getHistory(file);
            final History partHistory = bkRepo.getHistory(file, "1.2");
            // I made sure that each file had a 1.2

            validateHistory(fullHistory);
            validateHistory(partHistory);

            // Passing 1.2 to get History should remove 1.1 and 1.2
            // revisions from each file, so check number of entries.
            assertEquals(fullHistory.getHistoryEntries().size(),
                    (partHistory.getHistoryEntries().size() + 2), "Partial file history is wrong size");
        }
    }

    @Test
    public void testGetHistoryInvalid() {
        assertNotNull(bkRepo, "Couldn't read bitkeeper test repository.");

        final File file = new File(bkRepo.getDirectoryName(), "fakename.cpp");

        boolean caughtFull = false;
        try {
            final History fullHistory = bkRepo.getHistory(file);
        } catch (final HistoryException e) {
            caughtFull = true;
        }
        assertTrue(caughtFull, "No exception thrown by getHistory with fake file");

        boolean caughtPart = false;
        try {
            final History partHistory = bkRepo.getHistory(file, "1.2");
        } catch (final HistoryException e) {
            caughtPart = true;
        }
        assertTrue(caughtPart, "No exception thrown by getHistory with fake file");
    }

    private static String readStream(InputStream stream) {
        final Scanner scanner = new Scanner(stream).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    @Test
    public void testGetHistoryGet() {
        assertNotNull(bkRepo, "Couldn't read bitkeeper test repository.");

        for (final String bkFile : bkFiles) {
            final String currentVersion = readStream(bkRepo.getHistoryGet(bkRepo.getDirectoryName(), bkFile, "+"));
            final String firstVersion = readStream(bkRepo.getHistoryGet(bkRepo.getDirectoryName(), bkFile, "1.1"));

            assertNotNull(currentVersion, "Nothing returned by getHistoryGet.");
            assertNotNull(firstVersion, "Nothing returned by getHistoryGet.");
            assertThat("Files returned by getHistoryGet are incorrect.", currentVersion, not(equalTo(firstVersion)));
        }
    }

    @Test
    public void testGetHistoryGetInvalid() {
        assertNotNull(bkRepo, "Couldn't read bitkeeper test repository.");

        assertNull(bkRepo.getHistoryGet(bkRepo.getDirectoryName(), "fakename.cpp", "+"),
                "Something returned by getHistoryGet with fake file");
        assertNull(bkRepo.getHistoryGet(bkRepo.getDirectoryName(), "fakename.cpp", "1.1"),
                "Something returned by getHistoryGet with fake file");
    }

    @Test
    public void testAnnotation() throws Exception {
        assertNotNull(bkRepo, "Couldn't read bitkeeper test repository.");

        for (final String bkFile : bkFiles) {
            final File file = new File(bkRepo.getDirectoryName(), bkFile);
            final Annotation currentVersion = bkRepo.annotate(file, "+");
            final Annotation firstVersion = bkRepo.annotate(file, "1.1");

            assertEquals(currentVersion.getFilename(), file.getName(), "Wrong file returned by annotate.");
            assertEquals(firstVersion.getFilename(), file.getName(), "Wrong file returned by annotate.");
            assertTrue(currentVersion.getRevisions().size() > 1, "Incorrect revisions returned by annotate.");
            assertEquals(1, firstVersion.getRevisions().size(), "Incorrect revisions returned by annotate.");
        }
    }

    @Test
    public void testAnnotationInvalid() {
        assertNotNull(bkRepo, "Couldn't read bitkeeper test repository.");

        final File file = new File(bkRepo.getDirectoryName(), "fakename.cpp");

        boolean caughtCurrent = false;
        try {
            final Annotation currentVersion = bkRepo.annotate(file, "+");
        } catch (final IOException e) {
            caughtCurrent = true;
        }
        assertTrue(caughtCurrent, "No exception thrown by annotate with fake file");

        boolean caughtFirst = false;
        try {
            final Annotation firstVersion = bkRepo.annotate(file, "1.1");
        } catch (final IOException e) {
            caughtFirst = true;
        }
        assertTrue(caughtFirst, "No exception thrown by annotate with fake file");
    }
}
