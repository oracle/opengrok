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
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.security.Permission;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 *
 * @author Krystof Tulinger
 */
public class MessagesTest {

    private int portNum;
    private PrintStream stdout;
    private PrintStream stderr;
    private ByteArrayOutputStream newStdoutArray;
    private ByteArrayOutputStream newStderrArray;
    private PrintStream newStdout;
    private PrintStream newStderr;

    protected static class ExitException extends SecurityException {

        private int status;

        public ExitException(int status) {
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    private static class ExitExceptionSecurityManager extends SecurityManager {

        @Override
        public void checkPermission(Permission perm) {
            // allow anything.
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            // allow anything.
        }

        @Override
        public void checkExit(int status) {
            super.checkExit(status);
            System.out.println(status);
            throw new ExitException(status);
        }
    }

    protected int invokeMain() {
        return invokeMain("localhost", portNum);
    }

    protected int invokeMain(String address, int port) {
        try {
            Messages.main(new String[]{
                "-t", "sample message",
                "-s", address,
                "-p", "" + port,
                "-e", "" + System.currentTimeMillis() / 1000 + 100000});
        } catch (ExitException ex) {
            return ex.getStatus();
        }
        return 0;
    }

    @Before
    public void setUp() {
        portNum = 50000;
        System.setSecurityManager(new ExitExceptionSecurityManager());
        RuntimeEnvironment.getInstance().stopConfigurationListenerThread();

        while (!RuntimeEnvironment.getInstance().startConfigurationListenerThread(
                new InetSocketAddress("localhost", portNum))) {
            portNum++;
        }

        stdout = System.out;
        stderr = System.err;
        newStdout = new PrintStream(newStdoutArray = new ByteArrayOutputStream());
        newStderr = new PrintStream(newStderrArray = new ByteArrayOutputStream());
        System.setOut(newStdout);
        System.setErr(newStderr);
    }

    @After
    public void tearDown() {
        System.setSecurityManager(null);
        RuntimeEnvironment.getInstance().stopConfigurationListenerThread();
        System.setOut(stdout);
        System.setErr(stderr);
    }

    @Test
    public void testMessageSendSuccess() {
        RuntimeEnvironment.getInstance().setMessageLimit(100);
        Assert.assertEquals(0, invokeMain());
    }

    @Test
    public void testMessageSendWrongHost() {
        RuntimeEnvironment.getInstance().setMessageLimit(100);
        Assert.assertEquals(1, invokeMain("localhost", portNum + 2));
    }

    @Test
    public void testMessageSendOverLimit() {
        RuntimeEnvironment.getInstance().setMessageLimit(0);
        String output, outerr;

        Assert.assertEquals(1, invokeMain());
        output = new String(newStdoutArray.toByteArray(), Charset.defaultCharset());
        outerr = new String(newStderrArray.toByteArray(), Charset.defaultCharset());
        Assert.assertTrue(
                output.contains(String.format("%#x", Message.MESSAGE_LIMIT))
                || outerr.contains(String.format("%#x", Message.MESSAGE_LIMIT)));
    }
}
