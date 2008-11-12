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

package org.opensolaris.opengrok.management;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.util.FileUtilities;
import static org.junit.Assert.*;

public class OGAgentTest {

    private Properties savedProperties;
    private PrintStream savedSystemOut;
    private PrintStream savedSystemErr;
    private File logDir;

    @Before
    public void setUp() {
        savedProperties = (Properties) System.getProperties().clone();
        savedSystemOut = System.out;
        savedSystemErr = System.err;
    }

    @After
    public void tearDown() {
        if (savedProperties != null) {
            System.setProperties(savedProperties);
        }
        if (savedSystemOut != null) {
            System.setOut(savedSystemOut);
        }
        if (savedSystemErr != null) {
            System.setErr(savedSystemErr);
        }
        if (logDir != null && logDir.exists()) {
            FileUtilities.removeDirs(logDir);
        }
        // TODO We should stop the agent here, so that it doesn't interfere
        // with other tests. How can we do that?
    }

    /**
     * Create a directory we can use for logging. Will be stored in the field
     * {@link #logDir}.
     */
    private void createLogDir() throws IOException {
        assertNull(logDir);
        logDir = FileUtilities.createTemporaryDirectory("logdir");
    }

    /**
     * Test that messages with too low severity are not printed to the console.
     */
    @Test
    public void disableConsoleLogging() throws Exception {

        // Create a log directory
        createLogDir();
        String logDirName = logDir.getAbsolutePath();
        System.setProperty("org.opensolaris.opengrok.management.logging.path",
                           logDirName);

        // Disable console logging for FINE messages
        System.setProperty(
                "org.opensolaris.opengrok.management.logging.consolelevel",
                "INFO");

        // Redirect stdout and stderr
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream printer = new PrintStream(baos, true);
        System.setOut(printer);
        System.setErr(printer);

        OGAgent.main(new String[0]); // ) oga = new OGAgent();
        //oga.runOGA();
        assertTrue(baos.toString().contains("Logging to " + logDirName));
        baos.reset();
        String loggedMessage = "Should go to console!";
        String unloggedMessage = "Should not go to console";
        OpenGrokLogger.getLogger().severe(loggedMessage);
        OpenGrokLogger.getLogger().fine(unloggedMessage);
        assertTrue(baos.toString().contains(loggedMessage));
        assertFalse(baos.toString().contains(unloggedMessage));
    }
}
