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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.analysis;

import java.io.File;
import java.io.IOException;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
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
     * Test of doCtags method, of class Ctags.
     */
    @Test
    public void testDoCtags() throws Exception {                
     File file = new File(repository.getSourceRoot()+File.separator+"bug16070"+File.separator+"arguments.c");
     Definitions result = ctags.doCtags(file.getAbsolutePath()+"\n");
     assertEquals(13, result.numberOfSymbols());     
    }

}