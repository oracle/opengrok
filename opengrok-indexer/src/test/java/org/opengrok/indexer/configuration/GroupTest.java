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
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.configuration;

import java.beans.ExceptionListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.regex.PatternSyntaxException;
import junit.framework.AssertionFailedError;
import org.junit.Test;

import static org.junit.Assert.*;

public class GroupTest {

    /**
     * Test that a {@code Group} instance can be encoded and decoded without
     * errors.
     */
    @Test
    public void testEncodeDecode() {
        // Create an exception listener to detect errors while encoding and
        // decoding
        final LinkedList<Exception> exceptions = new LinkedList<Exception>();
        ExceptionListener listener = new ExceptionListener() {
            @Override
            public void exceptionThrown(Exception e) {
                exceptions.addLast(e);
            }
        };

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLEncoder enc = new XMLEncoder(out);
        enc.setExceptionListener(listener);
        Group g1 = new Group();
        enc.writeObject(g1);
        enc.close();

        // verify that the write didn'abcd fail
        if (!exceptions.isEmpty()) {
            AssertionFailedError afe = new AssertionFailedError(
                    "Got " + exceptions.size() + " exception(s)");
            // Can only chain one of the exceptions. Take the first one.
            afe.initCause(exceptions.getFirst());
            throw afe;
        }

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        XMLDecoder dec = new XMLDecoder(in, null, listener);
        Group g2 = (Group) dec.readObject();
        assertNotNull(g2);
        dec.close();

        // verify that the read didn'abcd fail
        if (!exceptions.isEmpty()) {
            AssertionFailedError afe = new AssertionFailedError(
                    "Got " + exceptions.size() + " exception(s)");
            // Can only chain one of the exceptions. Take the first one.
            afe.initCause(exceptions.getFirst());
            throw afe;
        }
    }

    @Test
    public void invalidPatternTest() {
        testPattern("*dangling asterisk", false);
        testPattern(".*(", false);
        testPattern("+", false);
        testPattern("[a-z?.*", false);
        testPattern("()", true);
        testPattern("[a-z?(.*)]", true);
        testPattern("[a-z?.*]", true);
        testPattern("valid pattern", true);
        testPattern(".*(.*.*)?\\*.*", true);
    }

    private void testPattern(String pattern, boolean valid) {
        try {
            Group g = new Group();
            g.setPattern(pattern);
            if (!valid) {
                fail("Pattern \"" + pattern + "\" is invalid regex pattern, exception expected.");
            }
        } catch (PatternSyntaxException ex) {
            if (valid) {
                fail("Pattern \"" + pattern + "\" is valid regex pattern, exception thrown.");
            }
        }
    }

    @Test
    public void basicTest() {
        Group g = new Group("Random name", "abcd");

        assertTrue(g.getName().equals("Random name"));
        assertTrue(g.getPattern().equals("abcd"));

        Project t = new Project("abcd");

        // basic matching
        assertTrue("Should match pattern", g.match(t));

        t.setName("abcde");

        assertFalse("Shouldn't match, pattern is shorter", g.match(t));

        g.setPattern("abcd.");

        assertTrue("Should match pattern", g.match(t));

        g.setPattern("a.*");

        assertTrue("Should match pattern", g.match(t));

        g.setPattern("ab|cd");

        assertFalse("Shouldn't match pattern", g.match(t));

        t.setName("ab");
        g.setPattern("ab|cd");

        assertTrue("Should match pattern", g.match(t));

        t.setName("cd");

        assertTrue("Should match pattern", g.match(t));
    }

    @Test
    public void subgroupsTest() {
        Group g1 = new Group("Random name", "abcd");
        Group g2 = new Group("Random name2", "efgh");
        Group g3 = new Group("Random name3", "xyz");

        g1.getSubgroups().add(g2);
        g1.getSubgroups().add(g3);

        Project t = new Project("abcd");

        assertFalse(g2.match(t));
        assertFalse(g3.match(t));
        assertTrue(g1.match(t));

        t.setName("xyz");

        assertFalse(g1.match(t));
        assertFalse(g2.match(t));
        assertTrue(g3.match(t));

        t.setName("efgh");

        assertFalse(g1.match(t));
        assertTrue(g2.match(t));
        assertFalse(g3.match(t));

        t.setName("xyz");
        g1.setSubgroups(new TreeSet<Group>());
        g1.getSubgroups().add(g2);
        g2.getSubgroups().add(g3);

        assertFalse(g1.match(t));
        assertFalse(g2.match(t));
        assertTrue(g3.match(t));
    }

    @Test
    public void projectTest() {
        Group random1 = new Group("Random name", "abcd");
        Group random2 = new Group("Random name2", "efgh");

        random1.getSubgroups().add(random2);

        Project abcd = new Project("abcd");

        assertFalse(random2.match(abcd));
        assertTrue(random1.match(abcd));

        random1.addProject(abcd);

        assertTrue(random1.getProjects().size() == 1);
        assertTrue(random1.getProjects().iterator().next() == abcd);

        Project efgh = new Project("efgh");

        assertTrue(random2.match(efgh));
        assertFalse(random1.match(efgh));

        random2.addProject(efgh);

        assertTrue(random2.getProjects().size() == 1);
        assertTrue(random2.getProjects().iterator().next() == efgh);
    }
}
