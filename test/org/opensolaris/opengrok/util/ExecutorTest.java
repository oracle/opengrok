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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Trond Norbye
 */
public class ExecutorTest {

    public ExecutorTest() {
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

    @Test
    public void test() throws IOException {
        List<String> cmdList = new ArrayList<String>();
        cmdList.add("echo");
        cmdList.add("testing org.opensolaris.opengrok.util.Executor");
        Executor instance = new Executor(cmdList);
        instance.exec();
        assertTrue(instance.get_stdout().startsWith("testing org.opensolaris.opengrok.util.Executor"));
        String err = instance.get_stderr();
        assertEquals(0, err.length());
        BufferedReader in = instance.get_stdout_reader();
        assertEquals("testing org.opensolaris.opengrok.util.Executor", in.readLine());
        in.close();
        in = instance.get_stderr_reader();
        assertNull(in.readLine());
        in.close();
    }


}