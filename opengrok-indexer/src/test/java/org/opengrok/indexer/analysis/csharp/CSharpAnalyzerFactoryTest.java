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
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.csharp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import java.io.InputStream;
import java.io.StringWriter;
import org.apache.lucene.document.Field;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.Ctags;
import org.opengrok.indexer.analysis.Scopes;
import org.opengrok.indexer.analysis.Scopes.Scope;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.opengrok.indexer.analysis.AnalyzerGuru.string_ft_nstored_nanalyzed_norms;

/**
 *
 * @author Tomas Kotal
 */
public class CSharpAnalyzerFactoryTest {

    private static Ctags ctags;
    private static TestRepository repository;
    private static AbstractAnalyzer analyzer;

    private static StreamSource getStreamSource(final String fname) {
        return new StreamSource() {
            @Override
            public InputStream getStream() throws IOException {
                return new FileInputStream(fname);
            }
        };
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        ctags = new Ctags();

        repository = new TestRepository();
        repository.create(CSharpAnalyzerFactoryTest.class.getResourceAsStream(
                "/org/opengrok/indexer/index/source.zip"));

        CSharpAnalyzerFactory analFact = new CSharpAnalyzerFactory();
        analyzer = analFact.getAnalyzer();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.validateUniversalCtags()) {
            analyzer.setCtags(new Ctags());
        }
    }

    @AfterClass
    public static void tearDownClass() {
        ctags.close();
        ctags = null;
    }

    /**
     * Test of writeXref method, of class CSharpAnalyzerFactory.
     * @throws Exception exception
     */
    @Test
    public void testScopeAnalyzer() throws Exception {
        String path = repository.getSourceRoot() + "/csharp/Sample.cs";
        File f = new File(path);
        if (!(f.canRead() && f.isFile())) {
            fail("csharp testfile " + f + " not found");
        }

        Document doc = new Document();
        doc.add(new Field(QueryBuilder.FULLPATH, path,
                string_ft_nstored_nanalyzed_norms));
        StringWriter xrefOut = new StringWriter();
        analyzer.setCtags(ctags);
        analyzer.setScopesEnabled(true);
        analyzer.analyze(doc, getStreamSource(path), xrefOut);

        IndexableField scopesField = doc.getField(QueryBuilder.SCOPES);
        assertNotNull(scopesField);
        Scopes scopes = Scopes.deserialize(scopesField.binaryValue().bytes);
        Scope globalScope = scopes.getScope(-1);
        assertEquals(4, scopes.size()); //TODO 5

        for (int i = 0; i < 41; ++i) {
            if (i == 10) {
                assertEquals("M1", scopes.getScope(i).getName());
                assertEquals("MyNamespace.TopClass", scopes.getScope(i).getNamespace());
            } else if (i >= 12 && i <= 14) {
                assertEquals("M2", scopes.getScope(i).getName());
                assertEquals("MyNamespace.TopClass", scopes.getScope(i).getNamespace());
            } else if (i >= 19 && i <= 25) {
                assertEquals("M3", scopes.getScope(i).getName());
                assertEquals("MyNamespace.TopClass", scopes.getScope(i).getNamespace());
//TODO add support for generic classes                
//            } else if (i >= 28 && i <= 30) { 
//                assertEquals("M4", scopes.getScope(i).name);
//                assertEquals("MyNamespace.TopClass", scopes.getScope(i).namespace);
            } else if (i >= 34 && i <= 36) {
                assertEquals("M5", scopes.getScope(i).getName());
                assertEquals("MyNamespace.TopClass.InnerClass", scopes.getScope(i).getNamespace());
            } else {
                assertEquals(scopes.getScope(i), globalScope);
                assertNull(scopes.getScope(i).getNamespace());
            }

        }
    }

}
