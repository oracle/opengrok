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
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author austvik
 */
public class HistoryEntryTest {

    private HistoryEntry instance;
    private Date historyDate = new Date();
    private String historyRevision = "1.0";
    private String historyAuthor = "test author";
    private String historyMessage = "history entry message";
 
    public HistoryEntryTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        instance = new HistoryEntry(historyRevision, historyDate,
            historyAuthor, null, historyMessage, true);
    }

    @After
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
        instance.addChangeRequest("CR1");
        instance.addChangeRequest("CR2");
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
        assertEquals(true, instance.isActive());
        instance.setActive(false);
        assertEquals(false, instance.isActive());
    }

    /**
     * Test of setActive method, of class HistoryEntry.
     */
    @Test
    public void setActive() {
        instance.setActive(true);
        assertEquals(true, instance.isActive());
        instance.setActive(false);
        assertEquals(false, instance.isActive());
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
        assertFalse(
            new History(Collections.singletonList(instance)).hasFileList());
        instance.addFile(fileName);
        assertTrue(instance.getFiles().contains(fileName));
        assertTrue(
            new History(Collections.singletonList(instance)).hasFileList());
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
        TreeSet<String> files = new TreeSet<String>();
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
     * Test of addChangeRequest method, of class HistoryEntry.
     */
    @Test
    public void addGetChangeRequest() {
        String changeRequest = "Change Request";
        assertEquals(0, instance.getChangeRequests().size());
        instance.addChangeRequest(changeRequest);
        assertEquals(1, instance.getChangeRequests().size());
        assertTrue(instance.getChangeRequests().contains(changeRequest));
    }

    /**
     * Test of setChangeRequests method, of class HistoryEntry.
     */
    @Test
    public void setChangeRequests() {
        List<String> changeRequests = new ArrayList<String>();
        changeRequests.add("CR1");
        changeRequests.add("CR2");
        instance.setChangeRequests(changeRequests);
        assertEquals(2, instance.getChangeRequests().size());
    }

    /**
     * Test of strip method, of class HistoryEntry.
     */
    @Test
    public void strip() {
        TreeSet<String> files = new TreeSet<String>();
        files.add("file1.file");
        files.add("file2.file");
        instance.setFiles(files);
        instance.setTags("test tag");
        instance.strip();
        assertEquals(0, instance.getFiles().size());
        assertEquals(null, instance.getTags());
    }

}
