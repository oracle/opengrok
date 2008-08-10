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
package org.opensolaris.opengrok.index;

import java.io.IOException;
import java.util.Iterator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Check the CommandLineOption class
 *
 * @author Trond Norbye
 */
public class CommandLineOptionsTest {

    public CommandLineOptionsTest() {
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
     * Test of getCommandString method, of class CommandLineOptions.
     */
    @Test
    public void testCommandLineOptions() throws IOException {
        CommandLineOptions instance = new CommandLineOptions();
        String cmdString = instance.getCommandString();
        assertNotNull(cmdString);

        int ii = 0;
        while (ii < cmdString.length()) {
            char c = cmdString.charAt(ii);
            if (c != ':') {
                assertNotNull(instance.getCommandUsage(c));
            }
            ++ii;
        }

        Iterator<CommandLineOptions.Option> iter = instance.getOptionsIterator();
        while (iter.hasNext()) {
            CommandLineOptions.Option o = iter.next();
            assertNotNull(o.description);
        }

        assertNotNull(instance.getUsage());
        assertNotNull(instance.getManPage());
    }
}