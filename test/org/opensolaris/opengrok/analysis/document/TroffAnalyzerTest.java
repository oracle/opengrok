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
 * Copyright 2009 - 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.analysis.document;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

import org.apache.lucene.document.Document;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.opensolaris.opengrok.web.Util;

/**
 * @author  Jens Elkner
 * @version $Revision$
 */
public class TroffAnalyzerTest {
    private static TroffAnalyzerFactory factory;
    private static TroffAnalyzer analyzer;
    private static String content;
    
    /**
     * Test method for {@link org.opensolaris.opengrok.analysis.document
     * .TroffAnalyzer#TroffAnalyzer(org.opensolaris.opengrok.analysis.FileAnalyzerFactory)}.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        factory = new TroffAnalyzerFactory();
        assertNotNull(factory);
        analyzer = new TroffAnalyzer(factory);
        assertNotNull(analyzer);
        String file = System.getProperty("opengrok.test.troff.doc",
            "testdata/sources/document/foobar.1");
        File f = new File(file);
        if (!(f.canRead() && f.isFile())) {
            fail("troff testfile " + f + " not found");
        }
        CharArrayWriter w = new CharArrayWriter((int)f.length());
        Util.dump(w, f, false);
        content = w.toString();
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception {
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
     * @throws IOException 
     */
    @Test
    public void testAnalyze() throws IOException {
        Document doc = new Document();
        analyzer.analyze(doc, new ByteArrayInputStream(content.getBytes()));
    }

    /**
     * Test method for {@link org.opensolaris.opengrok.analysis.document
     * .TroffAnalyzer#writeXref(java.io.Writer)}.
     * @throws IOException 
     */
    @Test
    public void testWriteXrefWriter() throws IOException {
        testAnalyze();
        StringWriter out = new StringWriter(content.length() + 1024);
        analyzer.writeXref(out);
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
