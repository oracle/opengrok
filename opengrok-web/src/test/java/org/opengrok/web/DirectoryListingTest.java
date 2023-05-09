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
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.opengrok.indexer.configuration.Filter;
import org.opengrok.indexer.configuration.IgnoredNames;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryEntry;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.search.DirectoryEntry;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.web.EftarFile;
import org.opengrok.indexer.web.EftarFileReader;
import org.opengrok.indexer.web.PathDescription;
import org.opengrok.indexer.web.Util;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Test that the {@link DirectoryListing#extraListTo(String, File, Writer, String, List)} produces the expected result.
 */
class DirectoryListingTest {

    /**
     * Indication of that the file was a directory and so that the size given by
     * the FS is platform dependent.
     */
    private static final int DIRECTORY_INTERNAL_SIZE = -2;
    /**
     * Indication that the date was not displayed.
     */
    private static final long NO_DATE = -2;
    /**
     * Indication of unparseable file size.
     */
    private static final int INVALID_SIZE = -1;

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static final String PROJECT_NAME = "git";

    private TestRepository repositories;

    private boolean savedUseHistoryCacheForDirectoryListing;

    private File directory;
    private List<FileEntry> entries;
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MMM-yyyy");

    static class FileEntry implements Comparable<FileEntry> {

        String name;
        String href;
        long lastModified;
        /**
         * FileEntry size. May be:
         * <pre>
         * positive integer - for a file
         * -2 - for a directory
         * -1 - for an unparseable size
         * </pre>
         */
        long size;
        String readableSize;
        List<FileEntry> subdirs;
        String pathDesc;

        FileEntry() {
        }

        private FileEntry(String name, String href, Long lastModified, long size, List<FileEntry> subdirs,
                          String pathDesc) {
            this.name = name;
            this.href = href;
            this.lastModified = lastModified;
            this.size = size;
            this.readableSize = Util.readableSize(size);
            this.subdirs = subdirs;
            this.pathDesc = pathDesc;
        }

        /**
         * Creating the directory entry.
         *
         * @param name name of the file
         * @param href href to the file
         * @param lastModified date of last modification
         * @param subdirs list of sub entries (may be empty)
         */
        FileEntry(String name, String href, long lastModified, List<FileEntry> subdirs) {
            this(name, href, lastModified, DIRECTORY_INTERNAL_SIZE, subdirs, null);
            assertNotNull(subdirs);
        }

        /**
         * Creating a regular file entry.
         *
         * @param name name of the file
         * @param href href to the file
         * @param lastModified date of last modification
         * @param size the desired size of the file on the disc
         */
        FileEntry(String name, String href, long lastModified, int size) {
            this(name, href, lastModified, size, null, null);
        }

        @Override
        public int compareTo(FileEntry fe) {
            int ret;

            if ((ret = name.compareTo(fe.name)) != 0) {
                return ret;
            }

            if ((ret = href.compareTo(fe.href)) != 0) {
                return ret;
            }

            if (!Objects.equals(pathDesc, fe.pathDesc)) {
                return -1;
            }

            if ((ret = Long.compare(lastModified, fe.lastModified)) != 0) {
                return ret;
            }

            // this is a file so the size must be exact
            if (subdirs == null) {
                if (fe.size == INVALID_SIZE) {
                    ret = readableSize.compareTo(fe.readableSize);
                } else {
                    ret = Long.compare(size, fe.size);
                }
            } else {
                // this is a directory so the size must have been "-" char
                if (size != DIRECTORY_INTERNAL_SIZE) {
                    ret = Long.compare(size, DIRECTORY_INTERNAL_SIZE);
                }
            }

            return ret;
        }
    }

    /**
     * Set up the test environment with repositories.
     */
    @BeforeEach
    public void setUp() throws Exception {
        repositories = new TestRepository();
        repositories.create(getClass().getResource("/repositories"));

        // Needed for HistoryGuru to operate normally.
        env.setRepositories(repositories.getSourceRoot());

        directory = new File(env.getSourceRootFile(), PROJECT_NAME);

        savedUseHistoryCacheForDirectoryListing = env.isUseHistoryCacheForDirectoryListing();

        // Need to populate list of ignored entries for all repository types.
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        RepositoryFactory.initializeIgnoredNames(env);

        // Needed to test the per repository merge changeset support flag is honored.
        // The repository will inherit the property from the project.
        env.setProjectsEnabled(true);
    }

    /**
     * Clean up after the test. Remove the test repositories.
     */
    @AfterEach
    public void tearDown() {
        repositories.destroy();
        repositories = null;

        env.setUseHistoryCacheForDirectoryListing(savedUseHistoryCacheForDirectoryListing);

        env.setIgnoredNames(new IgnoredNames());
        env.setIncludedNames(new Filter());

        env.getProjects().clear();
    }

    /**
     * Get the {@code href} attribute from: &lt;td align="left"&gt;&lt;tt&gt;&lt;a
     * href="foo" class="p"&gt;foo&lt;/a&gt;&lt;/tt&gt;&lt;/td&gt;.
     */
    private String getHref(Node item) {
        Node a = item.getFirstChild(); // a
        assertNotNull(a);
        assertEquals(Node.ELEMENT_NODE, a.getNodeType());

        Node href = a.getAttributes().getNamedItem("href");
        assertNotNull(href);
        assertEquals(Node.ATTRIBUTE_NODE, href.getNodeType());

        return href.getNodeValue();
    }

    /**
     * Get the filename from: &lt;td align="left"&gt;&lt;tt&gt;&lt;a href="foo"
     * class="p"&gt;foo&lt;/a&gt;&lt;/tt&gt;&lt;/td&gt;.
     */
    private String getFilename(Node item) {
        Node a = item.getFirstChild(); // a
        assertNotNull(a);
        assertEquals(Node.ELEMENT_NODE, a.getNodeType());

        Node node = a.getFirstChild();
        assertNotNull(node);
        // If this is element node then it is probably a directory in which case
        // it contains the &lt;b&gt; element.
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            node = node.getFirstChild();
            assertNotNull(node);
            assertEquals(Node.TEXT_NODE, node.getNodeType());
        } else {
            assertEquals(Node.TEXT_NODE, node.getNodeType());
        }

        return node.getNodeValue();
    }

    /**
     * Get the LastModified date from the &lt;td&gt;date&lt;/td&gt;.
     *
     * @param item the node representing &lt;td&gt
     * @return last modified date of the file
     * @throws java.lang.Exception if an error occurs
     */
    private long getDateValue(Node item) throws Exception {
        Node firstChild = item.getFirstChild();
        assertNotNull(firstChild);
        assertEquals(Node.TEXT_NODE, firstChild.getNodeType());

        String value = firstChild.getNodeValue();

        if (value.equalsIgnoreCase("Today")) {
            return Long.MAX_VALUE;
        }

        if (value.equals("-")) {
            return NO_DATE;
        }

        return dateFormatter.parse(value).getTime();
    }

    /**
     * Get the size from the: &lt;td&gt;&lt;tt&gt;size&lt;/tt&gt;&lt;/td&gt;.
     *
     * @param item the node representing &lt;td&gt;
     * @return positive integer if the record was a file<br>
     * {@link #INVALID_SIZE} if the size could not be parsed<br>
     * {@link #DIRECTORY_INTERNAL_SIZE} if the record was a directory<br>
     */
    private int getIntSize(Node item) throws NumberFormatException {
        Node val = item.getFirstChild();
        assertNotNull(val);
        assertEquals(Node.TEXT_NODE, val.getNodeType());
        if (DirectoryListing.BLANK_PLACEHOLDER.equals(val.getNodeValue().trim())) {
            // track that it had the DIRECTORY_SIZE_PLACEHOLDER character
            return DIRECTORY_INTERNAL_SIZE;
        }
        try {
            return Integer.parseInt(val.getNodeValue().trim());
        } catch (NumberFormatException ex) {
            return INVALID_SIZE;
        }
    }

    private String getStringSize(Node item) throws NumberFormatException {
        Node val = item.getFirstChild();
        assertNotNull(val);
        assertEquals(Node.TEXT_NODE, val.getNodeType());
        return val.getNodeValue().trim();
    }

    private @Nullable String getStringOrNull(Node item) throws NumberFormatException {
        Node val = item.getFirstChild();
        if (val == null) {
            return null;
        }
        assertEquals(Node.TEXT_NODE, val.getNodeType());
        return val.getNodeValue().trim();
    }

    /**
     * Validate this file-entry in the table.
     *
     * @param element The &lt;tr&gt; element
     */
    private void validateEntry(Element element, boolean usePathDescriptions) throws Exception {
        FileEntry entry = new FileEntry();
        NodeList nl = element.getElementsByTagName("td");
        int len = nl.getLength();
        // There should be 5 columns or fewer in the table.
        if (len < 5) {
            return;
        }

        if (usePathDescriptions) {
            assertTrue(len >= 7, "table <td> count");
        } else {
            assertEquals(7, len, "table <td> count");
        }

        // item(0) is a decoration placeholder, i.e. no content
        entry.name = getFilename(nl.item(1));
        entry.href = getHref(nl.item(1));
        entry.lastModified = getDateValue(nl.item(3));
        entry.size = getIntSize(nl.item(4));
        if (entry.size == INVALID_SIZE) {
            entry.readableSize = getStringSize(nl.item(4));
        }
        // item(5) and item(6) are Lines# and LOC, respectively.
        if (len > 7) {
            entry.pathDesc = getStringOrNull(nl.item(7));
        }

        // Try to look it up in the list of files.
        for (FileEntry e : entries) {
            if (e.compareTo(entry) == 0) {
                entries.remove(e);
                return;
            }
        }

        throw new AssertionError("Could not find a match for: " + entry.name);
    }

    private static Stream<Arguments> provideArgumentsForTestDirectoryListing() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(false, false)
                );
    }

    /**
     * Test directory listing.
     *
     * @throws java.lang.Exception if an error occurs while generating the list.
     */
    @ParameterizedTest
    @MethodSource("provideArgumentsForTestDirectoryListing")
    void testDirectoryListing(boolean useHistoryCache, boolean usePathDescriptions) throws Exception {

        env.setUseHistoryCacheForDirectoryListing(useHistoryCache);

        if (useHistoryCache) {
            Project project = new Project(PROJECT_NAME, env.getPathRelativeToSourceRoot(directory));
            project.setMergeCommitsEnabled(true);
            env.getProjects().put(PROJECT_NAME, project);
        }

        // Always create history cache. This should provide additional testing confidence
        // in how the useHistoryCacheForDirectoryListing indexer tunable is used.
        Indexer indexer = Indexer.getInstance();
        indexer.prepareIndexer(
                env, true, true,
                null, List.of(env.getPathRelativeToSourceRoot(directory)));

        Document document;
        if (usePathDescriptions) {
            File eftarFile = File.createTempFile("paths", ".eftar");
            Set<PathDescription> descriptions = new HashSet<>();
            descriptions.add(new PathDescription("/" + PROJECT_NAME + "/main.c",
                    "Description for main.c"));
            EftarFile ef = new EftarFile();
            ef.create(descriptions, eftarFile.getAbsolutePath());
            try (EftarFileReader eftarFileReader = new EftarFileReader(eftarFile)) {
                document = getDocumentWithDirectoryListing(eftarFileReader);
                // Construct the expected directory entries.
                setEntries(useHistoryCache, eftarFileReader);
            }
        } else {
            document = getDocumentWithDirectoryListing(null);
            // Construct the expected directory entries.
            setEntries(useHistoryCache, null);
        }

        // Verify the values of directory entries.
        NodeList nl = document.getElementsByTagName("tr");
        int len = nl.getLength();
        // Add one extra for header and one for parent directory link.
        assertEquals(entries.size() + 2, len);
        // Skip the header and parent link.
        for (int i = 2; i < len; ++i) {
            validateEntry((Element) nl.item(i), usePathDescriptions);
        }
    }

    private void setEntries(boolean useHistoryCache, EftarFileReader eftarFileReader) throws Exception {
        File[] files = directory.listFiles();
        assertNotNull(files);
        entries = new ArrayList<>();

        for (File file : files) {
            if (env.getIgnoredNames().ignore(file)) {
                continue;
            }

            HistoryGuru historyGuru = HistoryGuru.getInstance();
            // See the comment about always creating history cache in the caller.
            assertTrue(historyGuru.hasHistoryCacheForFile(file));

            String pathDesc = null;
            if (eftarFileReader != null) {
                EftarFileReader.FNode parentFNode = eftarFileReader.getNode("/" + PROJECT_NAME);
                if (parentFNode != null) {
                    pathDesc = eftarFileReader.getChildTag(parentFNode, file.getName());
                }
            }

            if (useHistoryCache) {
                if (file.isDirectory()) {
                    entries.add(new FileEntry(file.getName(), file.getName() + "/",
                            NO_DATE, DIRECTORY_INTERNAL_SIZE, null, pathDesc));
                } else {
                    HistoryEntry historyEntry = historyGuru.getLastHistoryEntry(file, true, false);
                    assertNotNull(historyEntry);
                    // The date string displayed in the UI has simple form so use the following
                    // to strip the minutes/seconds.
                    String dateString = dateFormatter.format(historyEntry.getDate());
                    long lastModTime = dateFormatter.parse(dateString).getTime();
                    entries.add(new FileEntry(file.getName(), file.getName(),
                            lastModTime, file.length(), null, pathDesc));
                }
            } else {
                // The date string displayed in the UI has simple form so use the following
                // to strip the minutes/seconds.
                Date date = new Date(file.lastModified());
                String dateString = dateFormatter.format(date);
                long lastModTime = dateFormatter.parse(dateString).getTime();

                // Special case for files modified today.
                if (System.currentTimeMillis() - lastModTime < 86400000) {
                    lastModTime = Long.MAX_VALUE;
                }

                if (file.isDirectory()) {
                    entries.add(new FileEntry(file.getName(), file.getName() + "/",
                            lastModTime, DIRECTORY_INTERNAL_SIZE, null, pathDesc));
                } else {
                    entries.add(new FileEntry(file.getName(), file.getName(),
                            lastModTime, file.length(), null, pathDesc));
                }
            }
        }
    }

    private Document getDocumentWithDirectoryListing(@Nullable EftarFileReader eftarFileReader) throws Exception {
        StringWriter outOrig = new StringWriter();
        StringWriter out = spy(outOrig);
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<start>\n");

        DirectoryListing instance;
        if (eftarFileReader != null) {
            instance = new DirectoryListing(eftarFileReader);
        } else {
            instance = new DirectoryListing();
        }
        assertNotNull(directory.list());
        instance.listTo("ctx", directory, out, directory.getName(),
                Arrays.asList(directory.list()));

        verify(out, never()).write((String) ArgumentMatchers.isNull());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        assertNotNull(factory, "DocumentBuilderFactory is null");

        DocumentBuilder builder = factory.newDocumentBuilder();
        assertNotNull(builder, "DocumentBuilder is null");

        out.append("</start>\n");
        String str = out.toString();
        // The XML parser does not like the '&nbsp;' so strip it away.
        str = str.replace("&nbsp;", " ");

        return builder.parse(new ByteArrayInputStream(str.getBytes()));
    }

    /**
     * Test that {@link EftarFileReader} exceptions are handled gracefully.
     */
    @Test
    void directoryListingWithEftarException() throws Exception {
        EftarFileReader mockReader = mock(EftarFileReader.class);
        when(mockReader.getNode(anyString())).thenThrow(IOException.class);
        DirectoryListing instance = new DirectoryListing(mockReader);
        File file = new File(directory, "foo");
        StringWriter mockWriter = spy(StringWriter.class);
        instance.extraListTo("ctx", directory, mockWriter, directory.getName(),
                Collections.singletonList(new DirectoryEntry(file)));
        verify(mockWriter, atLeast(20)).write(anyString());
    }
}
