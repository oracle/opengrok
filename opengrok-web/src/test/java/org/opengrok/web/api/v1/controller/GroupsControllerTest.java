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
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupsControllerTest extends OGKJerseyTest {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Override
    protected Application configure() {
        return new ResourceConfig(GroupsController.class);
    }

    private List<String> listGroups() {
        GenericType<List<String>> type = new GenericType<>() {
        };

        return target("groups")
                .request()
                .get(type);
    }

    @Test
    void emptyGroups() {
        env.setGroups(new HashMap<>());
        assertFalse(env.hasGroups());
        List<String> groups = listGroups();
        assertNotNull(groups);
        assertEquals(0, groups.size());
    }

    @Test
    void testGroupList() {
        Set<Group> groups = new TreeSet<>();
        Group group;
        group = new Group("group-foo", "project-(1|2|3)");
        groups.add(group);
        group = new Group("group-bar", "project-(7|8|9)");
        groups.add(group);
        env.setGroups(groups
                .stream()
                .collect(Collectors.toMap(Group::getName, Function.identity(), (v1, v2) -> v1)));
        assertTrue(env.hasGroups());

        List<String> groupsResult = listGroups();
        assertEquals(groups.stream().map(Group::getName).collect(Collectors.toSet()), new HashSet<>(groupsResult));
    }

    private List<String> listAllProjects(String groupName) {
        GenericType<List<String>> type = new GenericType<>() {
        };

        return target("groups")
                .path(groupName)
                .path("allprojects")
                .request()
                .get(type);
    }

    private Group setGroup(String groupName, String pattern) {
        Map<String, Group> groups = new TreeMap<>();
        Group group;
        group = new Group(groupName, pattern);
        groups.put(group.getName(), group);
        env.setGroups(groups);
        assertTrue(env.hasGroups());
        return group;
    }

    @Test
    void testGetAllProjectsEmpty() {
        String groupName = "group-empty";
        setGroup(groupName, "project-(1|2|3)");

        List<String> groupsResult = listAllProjects(groupName);
        assertNotNull(groupsResult);
        assertTrue(groupsResult.isEmpty());
    }

    @Test
    void testGetAllProjectsNonexistent() {
        env.setGroups(Collections.emptyMap());
        Map<String, Group> groupsEnv = env.getGroups();
        assertNotNull(groupsEnv);
        assertTrue(groupsEnv.isEmpty());

        assertThrows(NotFoundException.class, () -> listAllProjects("nonexistent-group"));
    }

    @Test
    void testGetAllProjects() {
        // Set projects.
        Project foo = new Project("project-1", "/foo");
        Project bar = new Project("project-2", "/foo-bar");
        HashMap<String, Project> projects = new HashMap<>();
        projects.put("foo", foo);
        projects.put("bar", bar);
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setProjectsEnabled(true);
        env.setProjects(projects);

        // Set group.
        String groupName = "group-all";
        Group group = setGroup(groupName, "project-(1|2|3)");

        // Verify that the group has the projects.
        Set<Project> expectedProjects = group.getAllProjects();
        assertFalse(expectedProjects.isEmpty());
        assertEquals(2, expectedProjects.size());

        List<String> groupsResult = listAllProjects(groupName);
        assertEquals(expectedProjects.stream().map(Project::getName).collect(Collectors.toList()),
                groupsResult);
    }

    @Test
    void testGetPatternNoGroup() {
        String groupName = "group-pattern";
        final String groupPattern = "^project-.*";
        setGroup(groupName, groupPattern);

        GenericType<String> type = new GenericType<>() {
        };

        assertThrows(NotFoundException.class, () -> target("groups")
                .path(groupName + "1")
                .path("pattern")
                .request()
                .get(type));
    }

    @Test
    void testGetPattern() {
        String groupName = "group-pattern";
        final String groupPattern = "^project-.*";
        setGroup(groupName, groupPattern);

        GenericType<String> type = new GenericType<>() {
        };

        String pattern = target("groups")
                .path(groupName)
                .path("pattern")
                .request()
                .get(type);
        assertNotNull(pattern);
        assertEquals(groupPattern, pattern);
    }

    @Test
    void testMatchNoGroup() {
        String groupName = "group-pattern-positive";
        final String groupPattern = "^project-.*";
        setGroup(groupName, groupPattern);

        Response response = target("groups")
                .path(groupName + "bar")
                .path("match")
                .request()
                .post(Entity.text("project-foo"));
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    void testMatchPositive() {
        String groupName = "group-pattern-positive";
        final String groupPattern = "^project-.*";
        setGroup(groupName, groupPattern);

        Response response = target("groups")
                .path(groupName)
                .path("match")
                .request()
                .post(Entity.text("project-foo"));
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    void testMatchNegative() {
        String groupName = "group-pattern-negative";
        final String groupPattern = "^project-.*";
        setGroup(groupName, groupPattern);

        Response response = target("groups")
                .path(groupName)
                .path("match")
                .request()
                .post(Entity.text("proj"));
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }
}
