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
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.configuration;

import java.beans.ExceptionListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTest {

    /**
     * Test that a {@code Project} instance can be encoded and decoded without
     * errors. Bug #3077.
     */
    @Test
    void testEncodeDecode() {
        // Create an exception listener to detect errors while encoding and
        // decoding
        final LinkedList<Exception> exceptions = new LinkedList<>();
        ExceptionListener listener = exceptions::addLast;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLEncoder enc = new XMLEncoder(out);
        enc.setExceptionListener(listener);
        Project p1 = new Project("foo");
        enc.writeObject(p1);
        enc.close();

        // verify that the write didn't fail
        if (!exceptions.isEmpty()) {
            // Can only chain one of the exceptions. Take the first one.
            throw new AssertionError("Got " + exceptions.size() + " exception(s)", exceptions.getFirst());
        }

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        XMLDecoder dec = new XMLDecoder(in, null, listener);
        Project p2 = (Project) dec.readObject();
        assertNotNull(p2);
        dec.close();

        // verify that the read didn't fail
        if (!exceptions.isEmpty()) {
            // Can only chain one of the exceptions. Take the first one.
            throw new AssertionError("Got " + exceptions.size() + " exception(s)", exceptions.getFirst());
        }
    }

    /**
     * Test project matching.
     */
    @Test
    void testGetProject() {
        // Create 2 projects, one being prefix of the other.
        Project foo = new Project("Project foo", "/foo");
        Project bar = new Project("Project foo-bar", "/foo-bar");

        // Make the runtime environment aware of these two projects.
        HashMap<String, Project> projects = new HashMap<>();
        projects.put("foo", foo);
        projects.put("bar", bar);
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setProjectsEnabled(true);
        env.setProjects(projects);

        // The matching of project name to project should be exact.
        assertAll(
                () -> assertEquals(foo, Project.getProject("/foo")),
                () -> assertEquals(bar, Project.getProject("/foo-bar")),
                () -> assertEquals(foo, Project.getProject("/foo/blah.c")),
                () -> assertEquals(bar, Project.getProject("/foo-bar/ha.c")),
                () -> assertNull(Project.getProject("/foof")),
                () -> assertNull(Project.getProject("/foof/ha.c"))
        );
    }

    /**
     * Test getProjectDescriptions().
     */
    @Test
    void testGetProjectDescriptions() {
        // Create 2 projects.
        Project foo = new Project("foo", "/foo");
        Project bar = new Project("bar", "/bar");

        // Make the runtime environment aware of these two projects.
        HashMap<String, Project> projects = new HashMap<>();
        projects.put("foo", foo);
        projects.put("bar", bar);
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setProjects(projects);

        List<String> descs = env.getProjectNames();
        assertAll(
                () -> assertTrue(descs.contains("foo")),
                () -> assertTrue(descs.contains("bar")),
                () -> assertFalse(descs.contains("foobar")),
                () -> assertEquals(2, descs.size())
        );
    }

    /**
     * Insert the value from configuration.
     */
    @Test
    void testMergeProjects1() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setTabSize(new Configuration().getTabSize() + 3731);
        env.setNavigateWindowEnabled(!new Configuration().isNavigateWindowEnabled());
        env.setBugPage("http://example.com/bugPage");
        env.setBugPattern("([1-9][0-9]{6,7})");
        env.setReviewPage("http://example.com/reviewPage");
        env.setReviewPattern("([A-Z]{2}ARC[ \\\\/]\\\\d{4}/\\\\d{3})");

        Project p1 = new Project();
        assertNotNull(p1);

        p1.completeWithDefaults();

        assertAll(
                () -> assertEquals(env.getTabSize(), p1.getTabSize()),
                () -> assertEquals(env.isNavigateWindowEnabled(), p1.isNavigateWindowEnabled()),
                () -> assertEquals(env.getBugPage(), p1.getBugPage()),
                () -> assertEquals(env.getBugPattern(), p1.getBugPattern()),
                () -> assertEquals(env.getReviewPage(), p1.getReviewPage()),
                () -> assertEquals(env.getReviewPattern(), p1.getReviewPattern())
        );
    }

    private static Stream<Arguments> provideArgumentsForTestHistoryAnnotationEnabled() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(false, false)
        );
    }

    /**
     * Assumes that the indexer uses {@link Project#completeWithDefaults()} when creating new projects.
     */
    @ParameterizedTest
    @MethodSource("provideArgumentsForTestHistoryAnnotationEnabled")
    void testHistoryAnnotationEnabled(boolean isHistoryEnabled, boolean useAnnotationCache) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        env.setHistoryEnabled(isHistoryEnabled);
        env.setUseAnnotationCache(useAnnotationCache);

        Project p1 = new Project();
        assertNotNull(p1);

        p1.completeWithDefaults();
        assertEquals(isHistoryEnabled, p1.isHistoryEnabled());
        assertEquals(useAnnotationCache, p1.isAnnotationCacheEnabled());
    }

    /**
     * Do not overwrite customized project property.
     */
    @Test
    void testMergeProjects2() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setTabSize(new Configuration().getTabSize() + 3731);

        Project p1 = new Project();
        p1.setTabSize(new Project().getTabSize() + 9737);
        p1.setNavigateWindowEnabled(true);
        p1.setHandleRenamedFiles(true);
        final String customBugPage = "http://example.com/bugPage";
        p1.setBugPage(customBugPage);
        final String customBugPattern = "([1-9][0-1]{6,7})";
        p1.setBugPattern(customBugPattern);
        final String customReviewPage = "http://example.com/reviewPage";
        p1.setReviewPage(customReviewPage);
        final String customReviewPattern = "([A-Z]{2}XYZ[ \\\\/]\\\\d{4}/\\\\d{3})";
        p1.setReviewPattern(customReviewPattern);

        p1.completeWithDefaults();

        assertAll(
                () -> assertNotNull(p1),
                () -> assertTrue(p1.isNavigateWindowEnabled(), "Navigate window should be turned on"),
                () -> assertTrue(p1.isHandleRenamedFiles(), "Renamed file handling should be true"),
                () -> assertEquals(new Project().getTabSize() + 9737, p1.getTabSize()),
                () -> assertEquals(p1.getBugPage(), customBugPage),
                () -> assertEquals(p1.getBugPattern(), customBugPattern),
                () -> assertEquals(p1.getReviewPage(), customReviewPage),
                () -> assertEquals(p1.getReviewPattern(), customReviewPattern)
        );
    }

    /**
     * Create a project fill with defaults from the configuration.
     */
    @Test
    void testCreateProjectWithConfiguration() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setTabSize(4);

        Project p1 = new Project("a", "/a");

        assertEquals(env.getTabSize(), p1.getTabSize());
    }

    @Test
    void testEquality() {
        Project g1 = new Project();
        Project g2 = new Project();
        assertEquals(g1, g2, "null == null");

        g1 = new Project("name");
        g2 = new Project("other");
        assertNotEquals(g1, g2, "\"name\" != \"other\"");
    }

    @Test
    void testUsername() {
        Project project = new Project();
        final String username = "foo";
        project.setUsername(username);
        assertEquals(username, project.getUsername());
    }

    @Test
    void testPassword() {
        Project project = new Project();
        final String password = "foo";
        project.setPassword(password);
        assertEquals(password, project.getPassword());
    }
}
