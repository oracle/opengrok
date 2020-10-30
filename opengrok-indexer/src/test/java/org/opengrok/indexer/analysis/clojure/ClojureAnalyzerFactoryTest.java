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
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.clojure;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.Ctags;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static org.opengrok.indexer.analysis.AnalyzerGuru.string_ft_nstored_nanalyzed_norms;

/**
 * @author Farid Zakaria
 */
public class ClojureAnalyzerFactoryTest {

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
        repository.create(ClojureAnalyzerFactoryTest.class.getResourceAsStream(
                "/org/opengrok/indexer/index/source.zip"));

        ClojureAnalyzerFactory analFact = new ClojureAnalyzerFactory();
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
     * Test of writeXref method, of class CAnalyzerFactory.
     *
     * @throws java.lang.Exception throw in case of analyzer or deserialize ctags error
     */
    @Test
    public void testScopeAnalyzer() throws Exception {
        String path = repository.getSourceRoot() + "/clojure/sample.clj";
        File f = new File(path);
        if (!(f.canRead() && f.isFile())) {
            fail("clojure testfile " + f + " not found");
        }

        Document doc = new Document();
        doc.add(new Field(QueryBuilder.FULLPATH, path,
                          string_ft_nstored_nanalyzed_norms));
        StringWriter xrefOut = new StringWriter();
        analyzer.setCtags(ctags);
        analyzer.analyze(doc, getStreamSource(path), xrefOut);

        Definitions definitions = Definitions.deserialize(doc.getField(QueryBuilder.TAGS).binaryValue().bytes);

        String[] type = new String[1];
        assertTrue(definitions.hasDefinitionAt("opengrok", 4, type));
        assertThat(type[0], is("namespace"));
        assertTrue(definitions.hasDefinitionAt("power-set", 8, type));
        assertThat(type[0], is("function"));
        assertTrue(definitions.hasDefinitionAt("power-set-private", 14, type));
        assertThat(type[0], is("privateFunction"));
        assertTrue(definitions.hasDefinitionAt("author", 19, type));
        assertThat(type[0], is("struct"));
        assertTrue(definitions.hasDefinitionAt("author-first-name", 22, type));
        assertThat(type[0], is("definition"));
        assertTrue(definitions.hasDefinitionAt("Farid", 24, type));
        assertThat(type[0], is("definition"));
    }

}
