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

package org.opensolaris.opengrok.history;

import java.io.File;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Lubos Kosco
 */
public class SCCSRepositoryTest {

    public SCCSRepositoryTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of isRepositoryFor method, of class SCCSRepository.
     */
    @Test
    public void testIsRepositoryFor() {
        //test bug 15954
        File tdir = new File(System.getProperty("java.io.tmpdir")+File.separator+"testogrepo");
        File test = new File(tdir,"Codemgr_wsdata");
        test.mkdirs();//TODO fix FileUtilities to not leave over dummy directories in tmp and then use them here ;)
        SCCSRepository instance = new SCCSRepository();        
        assertTrue(instance.isRepositoryFor(tdir));
        test.delete();
        tdir.delete();
    }

}