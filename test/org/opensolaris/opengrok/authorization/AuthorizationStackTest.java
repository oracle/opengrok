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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.authorization;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Krystof Tulinger
 */
public class AuthorizationStackTest {

    private Set<Group> envGroups;

    @Before
    public void setUp() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        envGroups = env.getGroups();
        env.setGroups(new TreeSet<>());
    }

    @After
    public void tearDown() {
        RuntimeEnvironment.getInstance().setGroups(envGroups);
    }

    @Test
    public void testForGroupsAndForProjectsDiscovery() {
        Group g1, g2, g3;
        AuthorizationEntity stack;

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        /**
         * Structure<br>
         * <pre>
         * G1 + P1
         *    + P2
         *    + P3
         *    + G2
         *       + P4
         *       + P5
         *       + P6
         *       + P7
         * G3 + P8
         *    + P9
         * </pre>
         */
        g1 = new Group();
        g1.setName("group 1");
        g1.addProject(new Project("project 1"));
        g1.addProject(new Project("project 2"));
        g1.addProject(new Project("project 3"));
        env.getGroups().add(g1);
        g2 = new Group();
        g2.setName("group 2");
        g2.addProject(new Project("project 4"));
        g2.addProject(new Project("project 5"));
        g2.addProject(new Project("project 6"));
        g2.addProject(new Project("project 7"));
        g1.addGroup(g2);
        env.getGroups().add(g2);
        g3 = new Group();
        g3.setName("group 3");
        g3.addProject(new Project("project 8"));
        g3.addProject(new Project("project 9"));
        env.getGroups().add(g3);

        // add g1 and all descendants their projects
        stack = new AuthorizationStack(AuthControlFlag.REQUIRED, "");
        stack.setForGroups(new TreeSet<>());
        stack.setForGroups("group 1");
        stack.load(new TreeMap<>());

        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"group 1", "group 2"})), stack.forGroups());
        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"project 1", "project 2", "project 3",
            "project 4", "project 5", "project 6", "project 7"})), stack.forProjects());

        // add group2, its parent g1 and g2 projects
        stack = new AuthorizationStack(AuthControlFlag.REQUIRED, "");
        stack.setForGroups(new TreeSet<>());
        stack.setForGroups("group 2");
        stack.load(new TreeMap<>());

        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"group 1", "group 2"})), stack.forGroups());
        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"project 4", "project 5", "project 6", "project 7"})), stack.forProjects());

        // add only g3 and its projects
        stack = new AuthorizationStack(AuthControlFlag.REQUIRED, "");
        stack.setForGroups(new TreeSet<>());
        stack.setForGroups("group 3");
        stack.load(new TreeMap<>());

        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"group 3"})), stack.forGroups());
        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"project 8", "project 9"})), stack.forProjects());
    }
}
