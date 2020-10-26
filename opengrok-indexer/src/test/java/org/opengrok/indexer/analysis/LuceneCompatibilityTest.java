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
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.opengrok.indexer.search.QueryBuilder;

/**
 * Need to have test-framework on the path. This will do a sanity
 * test on analyzers/tokenizers if they follow latest lucene asserts
 *
 * on compile test cp there needs to be lucene-test-framework, lucene-codecs and
 * randomizedtesting-runner on test src path there can be then the whole
 * test-framework/src from lucene
 *
 * @author Lubos Kosco
 */
public class LuceneCompatibilityTest extends TestCase {

    //TODO use reflection to init tests in case LUCENE_TEST_CLASS is present,
    // create the object out of it and call it's methods
    public LuceneCompatibilityTest() {
        super();
    }
    private static final String LUCENE_TEST_CLASS =
            "org.apache.lucene.analysis.BaseTokenStreamTestCase";
    private static final String LUCENE_TEST_METHOD = "assertTokenStreamContents";
    private static final String LUCENE_DEP =
            "com.carrotsearch.randomizedtesting.RandomizedTest";

    /**
     * Create a suite of tests to run. If the lucene test-framework classes are
     * not present, skip this test.
     *
     * @return tests to run
     */
    public static Test suite() {
        try {            
            Class.forName(LUCENE_DEP);
            Class.forName(LUCENE_TEST_CLASS);
            return new TestSuite(LuceneCompatibilityTest.class);
        } catch (ClassNotFoundException e) {
            return new TestSuite("LuceneCompatibility - empty (no external lucene test framework on classpath)");
        }
    }
    Analyzer testA;
    AnalyzerGuru guru;
    Method testM;
    Object testC = null;

    /**
     * Set up the test environment with repositories and a cache instance.
     */
    @Override
    protected void setUp() throws Exception {
        guru = new AnalyzerGuru();
        Class<?> c = Class.forName(LUCENE_TEST_CLASS);
        Class<?>[] argTypes = {TokenStream.class, String[].class, int[].class, int[].class, String[].class, int[].class,
                int[].class, Integer.class, boolean.class};
        testM = c.getDeclaredMethod(LUCENE_TEST_METHOD, argTypes);
    }

    public void testCompatibility() throws Exception {
        for (AnalyzerFactory fa : guru.getAnalyzerFactories()) {
            String input = "Hello world";
            String[] output = new String[]{"Hello", "world"};
            testA = fa.getAnalyzer();
            String name = testA.getClass().getName();
            //below analyzers have no refs

            // !!!!!!!!!!!!!!!!!!!!
            // below will fail for some analyzers because of the way how we 
            // deal with data - we don't use the reader, but cache the whole 
            // file instead inside "content" buffer (which is reused for xref)
            // !!!!!!!!!!!!!!!!!!!!
            try {
                if (!name.endsWith("FileAnalyzer") && !name.endsWith("BZip2Analyzer") && !name.endsWith("GZIPAnalyzer")
                        && !name.endsWith("XMLAnalyzer") && !name.endsWith("TroffAnalyzer") && !name.endsWith("ELFAnalyzer")
                        && !name.endsWith("JavaClassAnalyzer") && !name.endsWith("JarAnalyzer") && !name.endsWith("ZipAnalyzer")
                        //TODO below php and fortran analyzers have some problems with dummy input and asserts fail, 
                        // analyzers should properly set the tokens in case of wrongly formulated input
                        && !name.endsWith("TarAnalyzer") && !name.endsWith("PhpAnalyzer") && !name.endsWith("FortranAnalyzer")) {

                    System.out.println("Testing refs with " + name);
                    //BaseTokenStreamTestCase.assertTokenStreamContents(testA.tokenStream(
                    //        QueryBuilder.REFS, new StringReader(input)), output, null, null, null,
                    //        null, null, input.length());
                    testM.invoke(testC, testA.tokenStream(QueryBuilder.REFS,
                            new StringReader(input)), output, null, null, null, null, null,
                            input.length(), true);
                }
                output = new String[]{"hello", "world"};
                //below analyzers have no full, they just wrap data inside them         
                if (!name.endsWith("FileAnalyzer") && !name.endsWith("BZip2Analyzer") && !name.endsWith("GZIPAnalyzer")) {
                    System.out.println("Testing full with " + name);
                    //BaseTokenStreamTestCase.assertTokenStreamContents(testA.tokenStream(
                    //        QueryBuilder.FULL, new StringReader(input)), output, null, null, null,
                    //        null, null, input.length());
                    testM.invoke(testC, testA.tokenStream(QueryBuilder.FULL,
                            new StringReader(input)), output, null, null, null, null, null,
                            input.length(), true);
                }
            } catch (InvocationTargetException x) {
                Throwable cause = x.getCause();
                System.err.println(name + " failed: " + cause.getMessage() + " from " + LUCENE_TEST_CLASS + ":" + LUCENE_TEST_METHOD);
                throw (new Exception(cause));
            }
        }
    }
}
