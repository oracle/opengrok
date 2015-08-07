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
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
 * Portions copyright 2009 - 2011 Jens Elkner. 
 */
package org.opensolaris.opengrok.analysis.document;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import org.apache.lucene.document.Document;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.StreamSource;
import org.opensolaris.opengrok.util.TestRepository;
import org.opensolaris.opengrok.web.Util;

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
     * Test method for {@link org.opensolaris.opengrok.analysis.document
     * .TroffAnalyzer#TroffAnalyzer(org.opensolaris.opengrok.analysis.FileAnalyzerFactory)}.
     *
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        factory = new TroffAnalyzerFactory();
        assertNotNull(factory);
        analyzer = new TroffAnalyzer(factory);
        assertNotNull(analyzer);
        repository = new TestRepository();
        repository.create(TroffAnalyzerTest.class.getResourceAsStream(
                "/org/opensolaris/opengrok/index/source.zip"));

        String file = System.getProperty("opengrok.test.troff.doc",
                repository.getSourceRoot() + "/document/foobar.1");
        File f = new File(file);
        if (!(f.canRead() && f.isFile())) {
            fail("troff testfile " + f + " not found");
        }
        CharArrayWriter w = new CharArrayWriter((int) f.length());
        Util.dump(w, f, false);
        content = w.toString();
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        factory = null;
        analyzer = null;
        content = null;
        repository.destroy();
        repository = null;
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test method for {@link org.opensolaris.opengrok.analysis.document
     *  .TroffAnalyzer#analyze(org.apache.lucene.document.Document,
     *      java.io.InputStream)}.
     *
     * @throws IOException
     */
    @Test
    public void testAnalyze() throws IOException {
        Document doc = new Document();
        StringWriter xrefOut = new StringWriter();
        analyzer.analyze(doc, new StreamSource() {
            @Override
            public InputStream getStream() throws IOException {
                return new ByteArrayInputStream(content.getBytes());
            }
        }, xrefOut);
    }

    /**
     * Test method for {@link org.opensolaris.opengrok.analysis.document
     * .TroffAnalyzer#tokenStream(java.lang.String, java.io.Reader)}.
     */
    @Ignore
    public void testTokenStreamStringReader() {
        fail("Not yet implemented");
    }

    /**
     * Test method for {@link org.opensolaris.opengrok.analysis.document
     * .TroffAnalyzer#writeXref(java.io.Reader, java.io.Writer,
     *      org.opensolaris.opengrok.analysis.Definitions,
     *      org.opensolaris.opengrok.history.Annotation,
     *      org.opensolaris.opengrok.configuration.Project)}.
     */
    @Ignore
    public void xtestWriteXrefReaderWriterDefinitionsAnnotationProject() {
        fail("Not yet implemented");
    }
}
