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
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis.java;

import java.io.File;
import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import java.io.InputStream;
import java.io.StringWriter;
import org.apache.lucene.document.Field;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.opensolaris.opengrok.analysis.AnalyzerGuru.string_ft_nstored_nanalyzed_norms;
import org.opensolaris.opengrok.analysis.Ctags;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.Scopes;
import org.opensolaris.opengrok.analysis.Scopes.Scope;
import org.opensolaris.opengrok.analysis.StreamSource;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.search.QueryBuilder;

/**
 *
 * @author kotal
 */
public class JavaAnalyzerFactoryTest {
    
    FileAnalyzer analyzer;
    private String ctagsProperty = "org.opensolaris.opengrok.analysis.Ctags";
    private static Ctags ctags;

    private static String PATH = "org/opensolaris/opengrok/analysis/java/Sample.java";
    
    public JavaAnalyzerFactoryTest() {
        JavaAnalyzerFactory analFact = new JavaAnalyzerFactory();
        this.analyzer = analFact.getAnalyzer();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty(ctagsProperty, "ctags"));
        if (env.validateExuberantCtags()) {
            this.analyzer.setCtags(new Ctags());
        }
    }
    
    private static StreamSource getStreamSource(final String fname) {
        return new StreamSource() {
            @Override
            public InputStream getStream() throws IOException {
                return getClass().getClassLoader().getResourceAsStream(fname);
            }
        };
    }
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        ctags = new Ctags();
        ctags.setBinary(RuntimeEnvironment.getInstance().getCtags());
    }

    @AfterClass
    public static void tearDownClass() throws Exception {        
        ctags.close();
        ctags = null;
    }

    /**
     * Test of writeXref method, of class CAnalyzerFactory.
     */
    @Test
    public void testScopeAnalyzer() throws Exception {        
        Document doc = new Document();
        doc.add(new Field(QueryBuilder.FULLPATH, new File("test/" + PATH).getAbsolutePath(),
            string_ft_nstored_nanalyzed_norms));
        StringWriter xrefOut = new StringWriter();
        analyzer.setCtags(ctags);
        analyzer.setScopesEnabled(true);
        analyzer.analyze(doc, getStreamSource(PATH), xrefOut);
        
        IndexableField scopesField = doc.getField(QueryBuilder.SCOPES);
        assertNotNull(scopesField);
        Scopes scopes = Scopes.deserialize(scopesField.binaryValue().bytes);
        Scope globalScope = scopes.getScope(-1);
        assertEquals(3, scopes.size()); // foo, bar, main
        
        for (int i=0; i<50; ++i) {
            if (i >= 29 && i <= 31) {
                assertEquals("Sample", scopes.getScope(i).name);
                assertEquals("class:Sample", scopes.getScope(i).scope);
            } else if (i >= 33 && i <= 41) {
                assertEquals("Method", scopes.getScope(i).name);
                assertEquals("class:Sample", scopes.getScope(i).scope);
            } else if (i >= 45 && i <= 54) {
                assertEquals("InnerMethod", scopes.getScope(i).name);
                assertEquals("class:Sample.InnerClass", scopes.getScope(i).scope);
            } else {
                assertEquals(scopes.getScope(i), globalScope);
                assertNull(scopes.getScope(i).scope);
            }
        }
    }
    
}
