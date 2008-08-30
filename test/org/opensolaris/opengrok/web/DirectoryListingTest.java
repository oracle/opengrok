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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.web;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.util.FileUtilities;
import static org.junit.Assert.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * JUnit test to test that the DirectoryListing produce the expected result
 */
public class DirectoryListingTest {

    private File directory;
    private FileEntry[] entries;
    private SimpleDateFormat dateFormatter;

    class FileEntry implements Comparable {
        FileEntry() {
            dateFormatter = new SimpleDateFormat("dd-MMM-yyyy");
        }

        FileEntry(String name, long lastModified, int size) {
            this.name = name;
            this.lastModified = lastModified;
            this.size = size;
        }

        private void create() throws Exception {
            File file = new File(directory, name);
            if (!file.exists()) {
                assertTrue("Failed to create file", file.createNewFile());
            }

            long val = lastModified;
            if (val == Long.MAX_VALUE) {
                val = System.currentTimeMillis();
            }

            assertTrue("Failed to set modification time",
                       file.setLastModified(val));

            if (size > 0) {
                FileOutputStream out = new FileOutputStream(file);
                byte[] buffer = new byte[size];
                out.write(buffer);
                out.close();
            }
        }
        String name;
        long lastModified;
        int size;

        public int compareTo(Object o) {
            if (o instanceof FileEntry) {
                FileEntry fe = (FileEntry) o;

                // @todo verify all attributes!
                return name.compareTo(fe.name);
            } else {
                return -1;
            }
        }
    }

    public DirectoryListingTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        directory = FileUtilities.createTemporaryDirectory("directory");

        entries = new FileEntry[2];
        entries[0] = new FileEntry("foo", 0, 0);
        entries[1] = new FileEntry("bar", Long.MAX_VALUE, 0);

        for (FileEntry entry : entries) {
            entry.create();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (directory != null && directory.exists()) {
            removeDirectory(directory);
        }
    }

    private void removeDirectory(File dir) {
        File[] childs = dir.listFiles();
        if (childs != null) {
            for (File f : childs) {

                if (f.isDirectory()) {
                    removeDirectory(f);
                }
                f.delete();
            }
        }
    }

    /**
     * Get the filename from: &lt;td align="left"&gt;&lt;tt&gt;&lt;a href="foo"
     * class="p"&gt;foo&lt;/a&gt;&lt;/tt&gt;&lt;/td&gt;
     *
     * @param item
     * @return
     * @throws java.lang.Exception
     */
    private String getFilename(Node item) throws Exception {
        Node node = item.getFirstChild(); // tt
        assertNotNull(node);
        assertEquals(Node.ELEMENT_NODE, node.getNodeType());
        node = node.getFirstChild(); // a
        assertNotNull(node);
        assertEquals(Node.ELEMENT_NODE, node.getNodeType());
        node = node.getFirstChild();
        assertNotNull(node);
        assertEquals(Node.TEXT_NODE, node.getNodeType());
        return node.getNodeValue();
    }

    /**
     * Get the LastModified date from the &lt;td&gt;date&lt;/td&gt;
     * @todo fix the item
     * @param item the node representing &lt;td&gt
     * @return last modified date of the file
     * @throws java.lang.Exception if an error occurs
     */
    private long getLastModified(Node item) throws Exception {
        Node val = item.getFirstChild();
        assertNotNull(val);
        assertEquals(Node.TEXT_NODE, val.getNodeType());

        String value = val.getNodeValue();
        if (value.equalsIgnoreCase("Today")) {
            return Long.MAX_VALUE;
        } else {
            return dateFormatter.parse(value).getTime();
        }
    }

    /**
     * Get the size from the: &lt;td&gt;&lt;tt&gt;size&lt;/tt&gt;&lt;/td&gt;
     * @param item the node representing &lt;td&gt;
     * @return The size
     * @throws java.lang.Exception if an error occurs
     */
    private int getSize(Node item) throws Exception {
        Node child = item.getFirstChild();
        assertNotNull(child);
        Node val = child.getFirstChild();
        assertNotNull(val);
        assertEquals(Node.TEXT_NODE, val.getNodeType());
        return Integer.parseInt(val.getNodeValue());
    }

    /**
     * Validate this file-entry in the table
     * @param element The &lt;tr&gt; element
     * @throws java.lang.Exception
     */
    private void validateEntry(Element element) throws Exception {
        FileEntry entry = new FileEntry();
        NodeList nl = element.getElementsByTagName("td");
        int len = nl.getLength();
        if (len < 3) {
            return;
        }
        assertEquals(3, len);

        entry.name = getFilename(nl.item(0));
        entry.lastModified = getLastModified(nl.item(1));
        entry.size = getSize(nl.item(2));

        // Try to look it up in the list of files
        for (int ii = 0; ii < entries.length; ++ii) {
            if (entries[ii] != null && entries[ii].compareTo(entry) == 0) {
                entries[ii] = null;
                return;
            }
        }

        fail("Could not find a match for: " + entry.name);
    }

    /**
     * Test directory listing
     * @throws java.lang.Exception if an error occurs while generating the
     *         list.
     */
    @Test
    public void directoryListing() throws Exception {
        StringWriter out = new StringWriter();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        DirectoryListing instance = new DirectoryListing();
        instance.listTo(directory, out);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        assertNotNull("DocumentBuilderFactory is null", factory);

        DocumentBuilder builder = factory.newDocumentBuilder();
        assertNotNull("DocumentBuilder is null", out);

        String str = out.toString();
        Document document = builder.parse(new ByteArrayInputStream(str.getBytes()));

        NodeList nl = document.getElementsByTagName("tr");
        int len = nl.getLength();
        assertEquals(entries.length + 2, len);
        // Skip the .. entry and the header
        for (int i = 2; i < len; ++i) {
            validateEntry((Element) nl.item(i));
        }
    }
}