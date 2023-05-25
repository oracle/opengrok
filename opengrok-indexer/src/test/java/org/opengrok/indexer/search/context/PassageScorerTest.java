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
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.search.context;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.search.SearchEngine;
import org.opengrok.indexer.util.TestRepository;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.search.context.SearchAndContextFormatterTest.getFirstFragments;

/**
 * Make sure that passages within search results are ordered strictly based on the line numbers.
 */
public class PassageScorerTest {
    private static RuntimeEnvironment env;
    private static TestRepository repository;

    @BeforeAll
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResource("/sources"));

        env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty("org.opengrok.indexer.analysis.Ctags", "ctags"));
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        RepositoryFactory.initializeIgnoredNames(env);
        env.setHistoryEnabled(false);

        assertTrue(Paths.get(env.getSourceRootPath(), "c", "sdt.h").toFile().exists());

        Indexer.getInstance().doIndexerExecution(null, null);
    }

    @AfterAll
    public static void tearDownClass() {
        repository.destroy();
    }

    @Test
    void testSearch() throws Exception {
        SearchEngine instance = new SearchEngine();
        instance.setFreetext("DTRACE_PROBE4");
        instance.setFile("sdt");
        int noHits = instance.search();
        assertTrue(noHits > 0, "noHits should be positive");
        String[] frags = getFirstFragments(instance, env, new ContextArgs((short) 0, (short) 10));
        assertNotNull(frags, "getFirstFragments() should return something");
        assertEquals(1, frags.length, "frags should have one element");

        // Create XML from the result and parse it, get the line numbers, compare.
        String docString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<document>\n" +
                frags[0] +
                "\n</document>";

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        assertNotNull(factory, "DocumentBuilderFactory is null");

        DocumentBuilder builder = factory.newDocumentBuilder();
        assertNotNull(builder, "DocumentBuilder is null");

        final int[] lineNumbers = {58, 97, 155, 172, 189, 200, 232, 264, 297, 324};

        Document document = builder.parse(new ByteArrayInputStream(docString.getBytes()));
        NodeList nl = document.getElementsByTagName("a");
        assertEquals(lineNumbers.length, nl.getLength());
        int[] lines = new int[nl.getLength()];
        for (int i = 0; i < nl.getLength(); i++) {
            String href = nl.item(i).getAttributes().getNamedItem("href").getNodeValue();
            assertNotNull(href);
            String lineNumStr = href.substring(href.indexOf("#") + 1);
            assertNotNull(lineNumStr);
            int lineNum = Integer.parseInt(lineNumStr);
            lines[i] = lineNum;
        }
        assertEquals(0, Arrays.compare(lineNumbers, lines));

        instance.destroy();
    }
}
