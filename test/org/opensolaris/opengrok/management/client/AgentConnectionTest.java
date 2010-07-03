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

package org.opensolaris.opengrok.management.client;

import org.junit.Ignore;
import java.io.File;
import java.net.InetAddress;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.management.OGAgent;
import org.opensolaris.opengrok.util.FileUtilities;
import static org.junit.Assert.*;

@Ignore("Bug #16451")
public class AgentConnectionTest {

    private Properties savedProperties;

    @Before
    public void setUp() {
        savedProperties = (Properties) System.getProperties().clone();
    }

    @After
    public void tearDown() {
        if (savedProperties != null) {
            System.setProperties(savedProperties);
        }
    }

    @Test
    public void testAgentConnection() throws Exception {
        File logDir = FileUtilities.createTemporaryDirectory("logdir");
        System.setProperty("org.opensolaris.opengrok.management.logging.path",
                           logDir.getAbsolutePath());
//        OGAgent oga = new OGAgent();
//        oga.runOGA();
        OGAgent.main(new String[0]);
        String url = InetAddress.getLocalHost().getHostName() + ":" + 9292;
        AgentConnection ac = new AgentConnection(url);
        assertFalse("Shouldn't be connected", ac.isConnected());
        assertEquals(url, ac.getAgentURL());

        ac.connect();
        ac.registerListener();
        assertTrue("Should be connected", ac.isConnected());
        assertNotNull(ac.getMBeanServerConnection());

        ac.unregister();

        ac.reconnect(1);
        assertTrue("Not connected after reconnect", ac.isConnected());

        FileUtilities.removeDirs(logDir);
    }

}
