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
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import opengrok.auth.plugin.ldap.LdapServer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

class LdapServerTest {

    @Test
    void testInvalidURI() {
        LdapServer server = new LdapServer("foo:/\\/\\foo.bar");
        assertFalse(server.isReachable());
    }

    @Test
    void testGetPort() throws URISyntaxException {
        LdapServer server = new LdapServer("ldaps://foo.bar");
        assertEquals(636, server.getPort());

        server = new LdapServer("ldap://foo.bar");
        assertEquals(389, server.getPort());

        server = new LdapServer("crumble://foo.bar");
        assertEquals(-1, server.getPort());
    }

    @Test
    void testSetGetUsername() {
        LdapServer server = new LdapServer();

        assertNull(server.getUsername());
        assertNull(server.getPassword());

        final String testUsername = "foo";
        server.setUsername(testUsername);
        assertEquals(testUsername, server.getUsername());

        final String testPassword = "bar";
        server.setPassword(testPassword);
        assertEquals(testPassword, server.getPassword());
    }

    @Test
    void testIsReachablePositive() throws IOException, URISyntaxException {
        // Start simple TCP server on test port.
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        try (ServerSocket serverSocket = new ServerSocket(0, 1)) {
            int testPort = serverSocket.getLocalPort();

            // Mock getAddresses() to return single localhost IP address and getPort() to return the test port.
            LdapServer server = new LdapServer("ldaps://foo.bar.com");
            LdapServer serverSpy = Mockito.spy(server);
            doReturn(new InetAddress[] {loopbackAddress}).when(serverSpy).getAddresses(any());
            doReturn(testPort).when(serverSpy).getPort();

            // Test reachability.
            boolean reachable = serverSpy.isReachable();
            assertTrue(reachable);
        }
    }

    @Test
    void testsReachableNegative() throws Exception {
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();

        // Mock getAddresses() to return single localhost IP address and getPort() to return the test port.
        LdapServer server = new LdapServer("ldaps://foo.bar.com");
        LdapServer serverSpy = Mockito.spy(server);
        doReturn(new InetAddress[]{loopbackAddress}).when(serverSpy).getAddresses(any());
        // port 0 should not be reachable.
        doReturn(0).when(serverSpy).getPort();

        assertFalse(serverSpy.isReachable());
    }

    @Test
    void testEmptyAddressArray() throws UnknownHostException {
        LdapServer server = new LdapServer("ldaps://foo.bar.com");
        LdapServer serverSpy = Mockito.spy(server);
        doReturn(new InetAddress[]{}).when(serverSpy).getAddresses(any());
        assertFalse(serverSpy.isReachable());
    }

    @Test
    void testToString() {
        LdapServer server = new LdapServer("ldaps://foo.bar.com", "foo", "bar");
        server.setConnectTimeout(2000);
        server.setReadTimeout(1000);
        assertEquals("ldaps://foo.bar.com, connect timeout: 2000, read timeout: 1000, username: foo",
                server.toString());
    }
}
