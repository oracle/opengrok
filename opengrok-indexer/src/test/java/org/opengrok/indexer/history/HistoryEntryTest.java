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
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author austvik
 */
public class HistoryEntryTest {

    private HistoryEntry instance;
    private final Date historyDate = new Date();
    private final String historyRevision = "1.0.aaaaaa";
    private final String historyDisplayRevision = "1.0";
    private final String historyAuthor = "test author";
    private final String historyMessage = "history entry message";

    @BeforeEach
    public void setUp() {
        instance = new HistoryEntry(historyRevision, historyDisplayRevision, historyDate,
            historyAuthor, historyMessage, true, null);
    }

    @AfterEach
    public void tearDown() {
    }

    /**
     * Test of getLine method, of class HistoryEntry.
     */
    @Test
    public void getLine() {
        assertTrue(instance.getLine().contains(historyRevision));
        assertTrue(instance.getLine().contains(historyAuthor));
    }

    /**
     * Test of dump method, of class HistoryEntry.
     */
    @Test
    public void dump() {
        instance.dump();
        instance.setActive(false);
        instance.addFile("testFile1.txt");
        instance.addFile("testFile2.txt");
        instance.dump();
    }

    /**
     * Test of getAuthor method, of class HistoryEntry.
     */
    @Test
    public void getAuthor() {
        String result = instance.getAuthor();
        assertEquals(historyAuthor, result);
    }

    /**
     * Test of getDate method, of class HistoryEntry.
     */
    @Test
    public void getDate() {
        assertEquals(historyDate, instance.getDate());
        instance.setDate(null);
        assertNull(instance.getDate());
    }

    /**
     * Test of getMessage method, of class HistoryEntry.
     */
    @Test
    public void getMessage() {
        assertEquals(historyMessage, instance.getMessage());
    }

    /**
     * Test of getRevision method, of class HistoryEntry.
     */
    @Test
    public void getRevision() {
        assertEquals(historyRevision, instance.getRevision());
    }

    /**
     * Test of getDisplayRevision method, of class HistoryEntry.
     */
    @Test
    public void getDisplayRevision() {
        assertEquals(historyDisplayRevision, instance.getDisplayRevision());
    }

    /**
     * Test of setAuthor method, of class HistoryEntry.
     */
    @Test
    public void setAuthor() {
        String newAuthor = "New Author";
        instance.setAuthor(newAuthor);
        assertEquals(newAuthor, instance.getAuthor());
    }

    /**
     * Test of setDate method, of class HistoryEntry.
     */
    @Test
    public void setDate() {
        Date date = new Date();
        instance.setDate(date);
        assertEquals(date, instance.getDate());
    }

    /**
     * Test of isActive method, of class HistoryEntry.
     */
    @Test
    public void isActive() {
        assertTrue(instance.isActive());
        instance.setActive(false);
        assertFalse(instance.isActive());
    }

    /**
     * Test of setActive method, of class HistoryEntry.
     */
    @Test
    public void setActive() {
        instance.setActive(true);
        assertTrue(instance.isActive());
        instance.setActive(false);
        assertFalse(instance.isActive());
    }

    /**
     * Test of setMessage method, of class HistoryEntry.
     */
    @Test
    public void setMessage() {
        String message = "Something";
        instance.setMessage(message);
        assertEquals(message, instance.getMessage());
    }

    /**
     * Test of setRevision method, of class HistoryEntry.
     */
    @Test
    public void setRevision() {
        String revision = "1.2";
        instance.setRevision(revision);
        assertEquals(revision, instance.getRevision());
    }

    /**
     * Test of setDisplayRevision method, of class HistoryEntry.
     */
    @Test
    public void setDisplayRevision() {
        String displayRevision = "1.2";
        instance.setDisplayRevision(displayRevision);
        assertEquals(displayRevision, instance.getDisplayRevision());
        instance.setDisplayRevision(null);
        assertEquals(historyRevision, instance.getDisplayRevision());
    }

    /**
     * Test of appendMessage method, of class HistoryEntry.
     */
    @Test
    public void appendMessage() {
        String message = "Something Added";
        instance.appendMessage(message);
        assertTrue(instance.getMessage().contains(message));
    }

    /**
     * Test of addFile method, of class HistoryEntry.
     */
    @Test
    public void addFile() {
        String fileName = "test.file";
        HistoryEntry instance = new HistoryEntry();
        assertFalse(new History(Collections.singletonList(instance)).hasFileList());
        instance.addFile(fileName);
        assertTrue(instance.getFiles().contains(fileName));
        assertTrue(new History(Collections.singletonList(instance)).hasFileList());
    }

    /**
     * Test of getFiles method, of class HistoryEntry.
     */
    @Test
    public void getFiles() {
        String fileName = "test.file";
        instance.addFile(fileName);
        assertTrue(instance.getFiles().contains(fileName));
        assertEquals(1, instance.getFiles().size());
        instance.addFile("other.file");
        assertEquals(2, instance.getFiles().size());
    }

    /**
     * Test of setFiles method, of class HistoryEntry.
     */
    @Test
    public void setFiles() {
        TreeSet<String> files = new TreeSet<>();
        files.add("file1.file");
        files.add("file2.file");
        instance.setFiles(files);
        assertEquals(2, instance.getFiles().size());
    }

    /**
     * Test of toString method, of class HistoryEntry.
     */
    @Test
    public void testToString() {
        assertTrue(instance.toString().contains(historyRevision));
        assertTrue(instance.toString().contains(historyAuthor));
    }

    /**
     * Test of strip method, of class HistoryEntry.
     */
    @Test
    public void strip() {
        TreeSet<String> files = new TreeSet<>();
        files.add("file1.file");
        files.add("file2.file");
        instance.setFiles(files);
        instance.strip();
        assertEquals(0, instance.getFiles().size());
    }

    @Test
    public void testEqualsCopyConstructor() {
        HistoryEntry e = new HistoryEntry(instance);
        assertNotSame(e, instance);
        assertEquals(e, instance);
    }

    @Test
    public void testEqualsEmpty() {
        HistoryEntry e = new HistoryEntry();
        assertNotSame(e, instance);
        assertNotEquals(e, instance);
    }

    @Test
    public void testEquals() {
        HistoryEntry e = new HistoryEntry(historyRevision, historyDisplayRevision, historyDate,
                historyAuthor, historyMessage, true, null);
        assertNotSame(e, instance);
        assertEquals(e, instance);
    }

    @Test
    public void testNotEqualsRevision() {
        HistoryEntry e = new HistoryEntry(historyRevision + "0", historyDisplayRevision, historyDate,
                historyAuthor, historyMessage, true, null);
        assertNotSame(e, instance);
        assertNotEquals(e, instance);
    }

    @Test
    public void testNotEqualsDisplayRevision() {
        HistoryEntry e = new HistoryEntry(historyRevision, historyDisplayRevision + "0", historyDate,
                historyAuthor, historyMessage, true, null);
        assertNotSame(e, instance);
        assertNotEquals(e, instance);
    }

    @Test
    public void testEqualsWithFilesInstance() {
        HistoryEntry e = new HistoryEntry(historyRevision, historyDisplayRevision, historyDate,
                historyAuthor, historyMessage, true,
                Set.of(File.separator + Paths.get("foo", "main.o"),
                        File.separator + Paths.get("foo", "testsprog")));
        assertNotSame(e, instance);
        assertNotEquals(e, instance);
    }

    @Test
    public void testEqualsWithFilesPositive() {
        Set<String> files = Set.of(File.separator + Paths.get("foo", "main.o"),
                File.separator + Paths.get("foo", "testsprog"));
        HistoryEntry e1 = new HistoryEntry(historyRevision, historyDisplayRevision, historyDate,
                historyAuthor, historyMessage, true, files);
        HistoryEntry e2 = new HistoryEntry(historyRevision, historyDisplayRevision, historyDate,
                historyAuthor, historyMessage, true, files);
        assertNotSame(e1, e2);
        assertEquals(e1, e2);
    }

    @Test
    public void testEqualsWithFilesNegative() {
        String file1 = File.separator + Paths.get("foo", "main.o");
        String file2 = File.separator + Paths.get("foo", "testsprog");
        HistoryEntry e1 = new HistoryEntry(historyRevision, historyDisplayRevision, historyDate,
                historyAuthor, historyMessage, true,
                Set.of(file1, file2));
        HistoryEntry e2 = new HistoryEntry(historyRevision, historyDisplayRevision, historyDate,
                historyAuthor, historyMessage, true,
                Set.of(file1, file2 + "X"));
        assertNotSame(e1, e2);
        assertNotEquals(e1, e2);
    }
}
