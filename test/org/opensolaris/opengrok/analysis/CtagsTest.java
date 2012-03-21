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
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis;

import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.TestRepository;
import static org.junit.Assert.*;

/**
 *
 * @author Lubos Kosco
 */
public class CtagsTest {    
    private static Ctags ctags;
    private static TestRepository repository;

    public CtagsTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        ctags = new Ctags();
        ctags.setBinary(RuntimeEnvironment.getInstance().getCtags());

        /*
         * This setting is only needed for bug19195 but it does not seem
         * that it is possible to specify it just for single test case.
         * The config file contains assembly specific settings so it should
         * not be harmful to other test cases.
         */
        String extraOptionsFile = "testdata/sources/bug19195/ctags.config";
        ctags.setCTagsExtraOptionsFile(extraOptionsFile);

        assertTrue("No point in running ctags tests without valid ctags",
                RuntimeEnvironment.getInstance().validateExuberantCtags());
        repository = new TestRepository();
        repository.create(CtagsTest.class.getResourceAsStream(
                "/org/opensolaris/opengrok/index/source.zip"));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {        
        ctags.close();
        ctags = null;
        repository.destroy();
    }

    @Before
    public void setUp() throws IOException {        
    }

    @After
    public void tearDown() {        
    }

    /**
     * Helper method that gets the definitions for a file in the repository.
     * @param file file name relative to source root
     * @return the definitions found in the file
     */
    private static Definitions getDefs(String fileName) throws Exception {
        String path = repository.getSourceRoot() + File.separator
                + fileName.replace('/', File.separatorChar);
        return ctags.doCtags(new File(path).getAbsolutePath() + "\n");
    }

    /**
     * Test of doCtags method, of class Ctags.
     */
    @Test
    public void testDoCtags() throws Exception {
     Definitions result = getDefs("bug16070/arguments.c");
     assertEquals(13, result.numberOfSymbols());     
    }

    /**
     * Test that we don't get many false positives in the list of method
     * definitions for Java files. Bug #14924.
     */
    @Test
    public void bug14924() throws Exception {
        // Expected method names found in the file
        String[] names = {"ts", "classNameOnly", "format"};
        // Expected line numbers for the methods
        int[] lines = {44, 48, 53};

        Definitions result = getDefs("bug14924/FileLogFormatter.java");
        int count = 0;
        for (Definitions.Tag tag : result.getTags()) {
            if (tag.type.startsWith("method")) {
                assertTrue("too many methods", count < names.length);
                assertEquals("method name", names[count], tag.symbol);
                assertEquals("method line", lines[count], tag.line);
                count++;
            }
        }
        assertEquals("method count", names.length, count);
    }

    /**
     * Test that multiple extra command line options are processed correctly
     * for assembler source code. Bug #19195.
     */
    @Test
    public void bug19195() throws Exception {
        // Expected method names found in the file
        String[] names = {"foo", "bar", "_fce", "__fce"};
        // Expected line numbers for the methods
        int[] lines = {28, 51, 71, 71};

        /* Perform the actual test. */
        Definitions result = getDefs("bug19195/test.s");
        int count = 0;
        for (Definitions.Tag tag : result.getTags()) {
            if (tag.type.startsWith("function")) {
                assertTrue("too many functions", count < names.length);
                assertEquals("function name", names[count], tag.symbol);
                assertEquals("function line", lines[count], tag.line);
                count++;
            }
        }
        assertEquals("function count", names.length, count);
    }
}
