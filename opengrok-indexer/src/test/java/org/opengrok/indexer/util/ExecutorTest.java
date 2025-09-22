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
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author Trond Norbye
 */
class ExecutorTest {

    @Test
    void testConstructorWithTimeout() {
        int timeout = 42;
        Executor executor = new Executor(List.of("foo"), null, timeout);
        assertEquals(timeout, executor.getTimeout());
    }

    @Test
    void testString() {
        List<String> cmdList = new ArrayList<>();
        cmdList.add("echo");
        cmdList.add("testing org.opengrok.indexer.util.Executor");
        Executor instance = new Executor(cmdList);
        assertEquals(0, instance.exec());
        assertTrue(instance.getOutputString().startsWith("testing org.opengrok.indexer.util.Executor"));
        String err = instance.getErrorString();
        assertEquals(0, err.length());
    }

    @Test
    void testReader() throws IOException {
        List<String> cmdList = new ArrayList<>();
        cmdList.add("echo");
        cmdList.add("testing org.opengrok.indexer.util.Executor");
        Executor instance = new Executor(cmdList);
        assertEquals(0, instance.exec());
        Reader outputReader = instance.getOutputReader();
        assertNotNull(outputReader);
        BufferedReader in = new BufferedReader(outputReader);
        assertEquals("testing org.opengrok.indexer.util.Executor", in.readLine());
        in.close();
        Reader errorReader = instance.getErrorReader();
        assertNotNull(errorReader);
        in = new BufferedReader(errorReader);
        assertNull(in.readLine());
        in.close();
    }

    @Test
    void testStream() throws IOException {
        List<String> cmdList = new ArrayList<>();
        cmdList.add("echo");
        cmdList.add("testing org.opengrok.indexer.util.Executor");
        Executor instance = new Executor(cmdList, new File("."));
        assertEquals(0, instance.exec());
        assertNotNull(instance.getOutputStream());
        assertNotNull(instance.getErrorStream());
        Reader outputReader = instance.getOutputReader();
        assertNotNull(outputReader);
        BufferedReader in = new BufferedReader(outputReader);
        assertEquals("testing org.opengrok.indexer.util.Executor", in.readLine());
        in.close();
        Reader errorReader = instance.getErrorReader();
        assertNotNull(errorReader);
        in = new BufferedReader(errorReader);
        assertNull(in.readLine());
        in.close();
    }

    /**
     * Test setting environment variable. Assumes the {@code env} program exists
     * and reports list of environment variables along with their values.
     */
    @Test
    void testEnv() throws IOException {
        List<String> cmdList = List.of("env");
        final Map<String, String> env = new HashMap<>();
        env.put("foo", "bar");
        Executor instance = new Executor(cmdList, null, env);
        assertEquals(0, instance.exec());
        Reader outputReader = instance.getOutputReader();
        assertNotNull(outputReader);
        BufferedReader in = new BufferedReader(outputReader);
        String line;
        boolean found = false;
        while ((line = in.readLine()) != null) {
            if (line.equals("foo=bar")) {
                found = true;
                break;
            }
        }
        assertTrue(found);
        in.close();
    }

    /**
     * {@link Executor.StreamHandler} implementation that always fails with {@link IOException}.
     */
    private static class ErroneousStreamHandler implements Executor.StreamHandler {
        @Override
        public void processStream(InputStream input) throws IOException {
            throw new IOException("error");
        }
    }

    @Test
    void testStreamHandlerError() throws IOException {
        List<String> cmdList = new ArrayList<>();
        cmdList.add("echo");
        cmdList.add("testing org.opengrok.indexer.util.Executor");
        Executor instance = new Executor(cmdList, new File("."));
        assertEquals(-1, instance.exec(true, new ErroneousStreamHandler()));
        Assertions.assertAll(
                () -> assertNull(instance.getOutputStream()),
                () -> assertNull(instance.getErrorStream()),
                () -> assertNull(instance.getErrorReader()),
                () -> assertNull(instance.getOutputReader()));
    }

    @Test
    void testEscapeForBourneSingleLine() {
        List<String> argv = Arrays.asList("/usr/bin/foo", "--value=\n\r\f\u0011\t\\'");
        String s = Executor.escapeForShell(argv, false, false);
        assertEquals("/usr/bin/foo --value=$'\\n\\r\\f\\v\\t\\\\\\''", s);
    }

    @Test
    void testEscapeForBourneMultiLine() {
        List<String> argv = Arrays.asList("/usr/bin/foo", "--value", "\n\r\f\u0011\t\\'");
        String s = Executor.escapeForShell(argv, true, false);
        assertEquals("/usr/bin/foo \\" + System.lineSeparator() +
                "\t--value \\" + System.lineSeparator() +
                "\t$'\\n\\r\\f\\v\\t\\\\\\''", s);
    }

    @Test
    void testEscapeForWindowsSingleLine() {
        List<String> argv = Arrays.asList("C:\\foo", "--value=\n\r\f\u0011\t`\"$a", "/");
        String s = Executor.escapeForShell(argv, false, true);
        assertEquals("C:\\foo --value=\"`n`r`f`v`t```\"`$a\" /", s);
    }

    @Test
    void testEscapeForWindowsMultiLine() {
        List<String> argv = Arrays.asList("C:\\foo", "--value", "\n\r\f\u0011\t`\"$a", "/");
        String s = Executor.escapeForShell(argv, true, true);
        assertEquals("C:\\foo `" + System.lineSeparator() +
                "\t--value `" + System.lineSeparator() +
                "\t\"`n`r`f`v`t```\"`$a\" `" + System.lineSeparator() +
                "\t/", s);
    }
}
