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
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2009, 2011, Jens Elkner.
 */
package org.opengrok.indexer.analysis.document;

import java.io.ByteArrayInputStream;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import org.apache.lucene.document.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.web.Util;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Jens Elkner
 * @version $Revision$
 */
public class TroffAnalyzerTest {

    private static TroffAnalyzerFactory factory;
    private static TroffAnalyzer analyzer;
    private static String content;
    private static TestRepository repository;

    /**
     * Test method for {@link org.opengrok.indexer.analysis.document
     * .TroffAnalyzer#TroffAnalyzer(org.opengrok.indexer.analysis.FileAnalyzerFactory)}.
     *
     * @throws java.lang.Exception exception
     */
    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        factory = new TroffAnalyzerFactory();
        assertNotNull(factory);
        analyzer = new TroffAnalyzer(factory);
        assertNotNull(analyzer);
        repository = new TestRepository();
        repository.create(TroffAnalyzerTest.class.getClassLoader().getResource("sources"));

        String file = System.getProperty("opengrok.test.troff.doc",
                repository.getSourceRoot() + "/document/foobar.1");
        File f = new File(file);
        assertTrue(f.canRead() && f.isFile(), "troff testfile " + f + " not found");
        CharArrayWriter w = new CharArrayWriter((int) f.length());
        Util.dump(w, f, false);
        content = w.toString();
    }

    /**
     * @throws java.lang.Exception exception
     */
    @AfterAll
    public static void tearDownAfterClass() throws Exception {
        factory = null;
        analyzer = null;
        content = null;
        repository.destroy();
        repository = null;
    }

    /**
     * Test method for {@link org.opengrok.indexer.analysis.document
     *  .TroffAnalyzer#analyze(org.apache.lucene.document.Document,
     *      java.io.InputStream)}.
     *
     * @throws IOException I/O exception
     */
    @Test
    void testAnalyze() throws IOException {
        Document doc = new Document();
        StringWriter xrefOut = new StringWriter();
        analyzer.analyze(doc, new StreamSource() {
            @Override
            public InputStream getStream() throws IOException {
                return new ByteArrayInputStream(content.getBytes());
            }
        }, xrefOut);
    }

}
