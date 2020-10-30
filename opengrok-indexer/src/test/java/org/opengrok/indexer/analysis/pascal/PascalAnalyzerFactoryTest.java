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
package org.opengrok.indexer.analysis.pascal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import static org.hamcrest.CoreMatchers.is;
import org.junit.AfterClass;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.opengrok.indexer.analysis.AnalyzerGuru.string_ft_nstored_nanalyzed_norms;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.Ctags;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.TestRepository;

/**
 *
 * @author alexanthony
 */
public class PascalAnalyzerFactoryTest {
    
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
        repository.create(PascalAnalyzerFactoryTest.class.getResourceAsStream(
                "/org/opengrok/indexer/index/source.zip"));

        PascalAnalyzerFactory analyzerFactory = new PascalAnalyzerFactory();
        analyzer = analyzerFactory.getAnalyzer();
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
     * Test of writeXref method, of class PascalAnalyzerFactory.
     *
     * @throws java.lang.Exception exception
     */
    @Test
    public void testAnalyzer() throws Exception {
        String path = repository.getSourceRoot() + "/pascal/Sample.pas";
        File f = new File(path);
        if (!(f.canRead() && f.isFile())) {
            fail("pascal testfile " + f + " not found");
        }

        Document doc = new Document();
        doc.add(new Field(QueryBuilder.FULLPATH, path,
                string_ft_nstored_nanalyzed_norms));
        StringWriter xrefOut = new StringWriter();
        analyzer.setCtags(ctags);
        analyzer.setScopesEnabled(true);
        analyzer.analyze(doc, getStreamSource(path), xrefOut);

        Definitions definitions = Definitions.deserialize(doc.getField(QueryBuilder.TAGS).binaryValue().bytes);
        assertNotNull(definitions);
        String[] type = new String[1];
        assertTrue(definitions.hasDefinitionAt("Sample", 22, type));
        assertThat(type[0], is("unit"));
        assertTrue(definitions.hasDefinitionAt("TSample", 28, type));
        assertThat(type[0], is("Class"));
        assertTrue(definitions.hasDefinitionAt("Id", 40, type));
        assertThat(type[0], is("property"));
        assertTrue(definitions.hasDefinitionAt("Description", 41, type));
        assertThat(type[0], is("property"));
        assertTrue(definitions.hasDefinitionAt("TSample.GetId", 48, type));
        assertThat(type[0], is("function"));
        assertTrue(definitions.hasDefinitionAt("TSample.SetId", 53, type));
        assertThat(type[0], is("procedure"));
        assertTrue(definitions.hasDefinitionAt("TSample.GetClassName", 58, type));
        assertThat(type[0], is("function"));
        assertTrue(definitions.hasDefinitionAt("TSample.GetUser", 63, type));
        assertThat(type[0], is("function"));
    }

}
