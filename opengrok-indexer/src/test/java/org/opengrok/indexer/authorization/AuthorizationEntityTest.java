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
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.authorization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Krystof Tulinger
 */
@RunWith(Parameterized.class)
public class AuthorizationEntityTest {

    private Set<Group> envGroups;
    private Map<String, Project> envProjects;

    private final Function<Void, AuthorizationEntity> authEntityFactory;

    private static final Function<Void, AuthorizationEntity> PLUGIN_FACTORY = new Function<Void, AuthorizationEntity>() {
        @Override
        public AuthorizationEntity apply(Void t) {
            return new AuthorizationPlugin(AuthControlFlag.REQUIRED, "");
        }
    };

    private static final Function<Void, AuthorizationEntity> STACK_FACTORY = new Function<Void, AuthorizationEntity>() {
        @Override
        public AuthorizationEntity apply(Void t) {
            return new AuthorizationStack(AuthControlFlag.REQUIRED, "");
        }
    };

    @Parameterized.Parameters
    public static Collection<Function<Void, AuthorizationEntity>> parameters() {
        List<Function<Void, AuthorizationEntity>> l = new ArrayList<>();
        l.add(PLUGIN_FACTORY);
        l.add(STACK_FACTORY);
        return l;
    }

    public AuthorizationEntityTest(Function<Void, AuthorizationEntity> authEntityFactory) {
        this.authEntityFactory = authEntityFactory;
    }

    @Before
    public void setUp() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        envGroups = env.getGroups();
        envProjects = env.getProjects();
        env.setGroups(new TreeSet<>());
        env.setProjects(new TreeMap<>());
    }

    @After
    public void tearDown() {
        RuntimeEnvironment.getInstance().setGroups(envGroups);
        RuntimeEnvironment.getInstance().setProjects(envProjects);
    }

    @Test
    public void testForGroupsAndForProjectsDiscovery() {
        Group g1, g2, g3;
        AuthorizationEntity authEntity;

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setProjectsEnabled(true);

        env.getProjects().put("project 1", new Project("project 1"));
        env.getProjects().put("project 2", new Project("project 2"));
        env.getProjects().put("project 3", new Project("project 3"));
        env.getProjects().put("project 4", new Project("project 4"));
        env.getProjects().put("project 5", new Project("project 5"));
        env.getProjects().put("project 6", new Project("project 6"));
        env.getProjects().put("project 7", new Project("project 7"));
        env.getProjects().put("project 8", new Project("project 8"));
        env.getProjects().put("project 9", new Project("project 9"));

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
        g1.addProject(env.getProjects().get("project 1"));
        g1.addProject(env.getProjects().get("project 2"));
        g1.addProject(env.getProjects().get("project 3"));
        env.getGroups().add(g1);
        g2 = new Group();
        g2.setName("group 2");
        g2.addProject(env.getProjects().get("project 4"));
        g2.addProject(env.getProjects().get("project 5"));
        g2.addProject(env.getProjects().get("project 6"));
        g2.addProject(env.getProjects().get("project 7"));
        g1.addGroup(g2);
        env.getGroups().add(g2);
        g3 = new Group();
        g3.setName("group 3");
        g3.addProject(env.getProjects().get("project 8"));
        g3.addProject(env.getProjects().get("project 9"));
        env.getGroups().add(g3);

        // add g1 and all descendants their projects
        authEntity = authEntityFactory.apply(null);
        authEntity.setForGroups(new TreeSet<>());
        authEntity.setForGroups("group 1");
        authEntity.load(new TreeMap<>());

        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"group 1", "group 2"})), authEntity.forGroups());
        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"project 1", "project 2", "project 3",
            "project 4", "project 5", "project 6", "project 7"})), authEntity.forProjects());

        // add group2, its parent g1 and g2 projects
        authEntity = authEntityFactory.apply(null);
        authEntity.setForGroups(new TreeSet<>());
        authEntity.setForGroups("group 2");
        authEntity.load(new TreeMap<>());

        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"group 1", "group 2"})), authEntity.forGroups());
        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"project 4", "project 5", "project 6", "project 7"})), authEntity.forProjects());

        // add only g3 and its projects
        authEntity = authEntityFactory.apply(null);
        authEntity.setForGroups(new TreeSet<>());
        authEntity.setForGroups("group 3");
        authEntity.load(new TreeMap<>());

        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"group 3"})), authEntity.forGroups());
        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"project 8", "project 9"})), authEntity.forProjects());
    }

    /**
     * Listed projects don't exist.
     */
    @Test
    public void testForGroupsAndForProjectsDiscoveryInvalidProject() {
        AuthorizationEntity authEntity = authEntityFactory.apply(null);

        authEntity.setForProjects(new TreeSet<>(Arrays.asList(new String[]{"project 1", "project 2", "project 3",
            "project 4", "project 5", "project 6", "project 7"})));

        authEntity.load(new TreeMap<>());

        assertEquals(new TreeSet<>(), authEntity.forGroups());
        assertEquals(new TreeSet<>(), authEntity.forProjects());
    }

    /**
     * Listed groups don't exist.
     */
    @Test
    public void testForGroupsAndForProjectsDiscoveryInvalidGroup() {
        AuthorizationEntity authEntity = authEntityFactory.apply(null);

        authEntity.setForGroups(new TreeSet<>(Arrays.asList(new String[]{"group 1", "group 2"})));

        authEntity.load(new TreeMap<>());

        assertEquals(new TreeSet<>(), authEntity.forGroups());
        assertEquals(new TreeSet<>(), authEntity.forProjects());
    }

    /**
     * Listed projects in the group don't exist.
     */
    @Test
    public void testForGroupsAndForProjectsDiscoveryInvalidProjectInGroup() {
        AuthorizationEntity authEntity = authEntityFactory.apply(null);

        authEntity.setForGroups(new TreeSet<>(Arrays.asList(new String[]{"group 1", "group 2"})));
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        Group g1 = new Group();
        g1.setName("group 1");
        g1.addProject(new Project("project 1"));
        g1.addProject(new Project("project 2"));
        g1.addProject(new Project("project 3"));
        env.getGroups().add(g1);

        authEntity.load(new TreeMap<>());

        assertEquals(new TreeSet<>(Arrays.asList(new String[]{"group 1"})), authEntity.forGroups());
        assertEquals(new TreeSet<>(), authEntity.forProjects());
    }
}
