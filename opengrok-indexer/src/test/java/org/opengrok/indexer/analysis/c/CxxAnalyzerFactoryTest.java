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
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.analysis.AnalyzerGuru.string_ft_nstored_nanalyzed_norms;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexableField;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.Ctags;
import org.opengrok.indexer.analysis.Scopes;
import org.opengrok.indexer.analysis.Scopes.Scope;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.TestRepository;

/**
 *
 * @author Tomas Kotal
 */
public class CxxAnalyzerFactoryTest {

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

    @BeforeAll
    public static void setUpClass() throws Exception {
        ctags = new Ctags();

        repository = new TestRepository();
        repository.create(CxxAnalyzerFactoryTest.class.getClassLoader().getResource("sources"));

        CxxAnalyzerFactory analFact = new CxxAnalyzerFactory();
        analyzer = analFact.getAnalyzer();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.validateUniversalCtags()) {
            analyzer.setCtags(new Ctags());
        }
    }

    @AfterAll
    public static void tearDownClass() {
        ctags.close();
        ctags = null;
    }

    /**
     * Test of writeXref method, of class CAnalyzerFactory.
     * @throws Exception exception
     */
    @Test
    void testScopeAnalyzer() throws Exception {
        String path = repository.getSourceRoot() + "/c/sample.cxx";
        File f = new File(path);
        assertTrue(f.canRead() && f.isFile(), "cxx testfile " + f + " not found");

        Document doc = new Document();
        doc.add(new Field(QueryBuilder.FULLPATH, path,
                string_ft_nstored_nanalyzed_norms));
        StringWriter xrefOut = new StringWriter();
        analyzer.setCtags(ctags);
        analyzer.setScopesEnabled(true);
        System.out.println(path);

        analyzer.analyze(doc, getStreamSource(path), xrefOut);

        IndexableField scopesField = doc.getField(QueryBuilder.SCOPES);
        assertNotNull(scopesField);
        Scopes scopes = Scopes.deserialize(scopesField.binaryValue().bytes);
        Scope globalScope = scopes.getScope(-1);
        assertEquals(9, scopes.size());

        for (int i = 0; i < 50; ++i) {
            if (i >= 11 && i <= 15) {
                assertEquals("SomeClass", scopes.getScope(i).getName());
                assertEquals("SomeClass", scopes.getScope(i).getNamespace());
            } else if (i >= 17 && i <= 20) {
                assertEquals("~SomeClass", scopes.getScope(i).getName());
                assertEquals("SomeClass", scopes.getScope(i).getNamespace());
            } else if (i >= 22 && i <= 25) {
                assertEquals("MemberFunc", scopes.getScope(i).getName());
                assertEquals("SomeClass", scopes.getScope(i).getNamespace());
            } else if (i >= 27 && i <= 29) {
                assertEquals("operator ++", scopes.getScope(i).getName());
                assertEquals("SomeClass", scopes.getScope(i).getNamespace());
            } else if (i >= 32 && i <= 34) {
                assertEquals("TemplateMember", scopes.getScope(i).getName());
                assertEquals("SomeClass", scopes.getScope(i).getNamespace());
            } else if (i >= 44 && i <= 46) {
                assertEquals("SomeFunc", scopes.getScope(i).getName());
                assertEquals("ns1::NamespacedClass", scopes.getScope(i).getNamespace());
            } else if (i >= 51 && i <= 54) {
                assertEquals("foo", scopes.getScope(i).getName());
                assertNull(scopes.getScope(i).getNamespace());
            } else if (i >= 59 && i <= 73) {
                assertEquals("bar", scopes.getScope(i).getName());
                assertNull(scopes.getScope(i).getNamespace());
            } else if (i >= 76 && i <= 87) {
                assertEquals("main", scopes.getScope(i).getName());
                assertNull(scopes.getScope(i).getNamespace());
            } else {
                assertEquals(scopes.getScope(i), globalScope);
                assertNull(scopes.getScope(i).getNamespace());
            }
        }
    }

}
