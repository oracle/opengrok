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
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opengrok.indexer.authorization.AuthorizationStack;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.web.QueryParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PageConfigRequestedProjectsTest {

    final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private Map<String, Project> oldProjects;
    private Map<String, Group> oldGroups;
    private AuthorizationStack oldPluginStack;

    @BeforeEach
    public void setUp() {
        oldProjects = env.getProjects();
        oldGroups = env.getGroups();
        oldPluginStack = env.getPluginStack();

        Map<String, Group> groups = new TreeMap<>();
        Map<String, Project> projects = new TreeMap<>();

        for (int i = 0; i < 10; i++) {
            Project project = new Project();
            project.setName("project-" + i);
            project.setPath("/project-" + i);
            project.setIndexed(true);

            projects.put("project-" + i, project);
        }

        Group group;
        group = new Group();
        group.setName("group-1-2-3");
        group.setPattern("project-(1|2|3)");
        groups.put(group.getName(), group);

        group = new Group();
        group.setName("group-7-8-9");
        group.setPattern("project-(7|8|9)");
        groups.put(group.getName(), group);

        env.setGroups(groups);
        env.setProjects(projects);
        env.setProjectsEnabled(true);
        env.setPluginStack(null);

        env.applyConfig(false, CommandTimeoutType.INDEXER);
    }

    @AfterEach
    public void tearDown() {
        env.setProjects(oldProjects);
        env.setGroups(oldGroups);
        env.setPluginStack(oldPluginStack);
    }

    @Test
    public void testSingleProject() {
        final HttpServletRequest request = createRequest(new String[]{"project-1"}, null);

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(Arrays.asList("project-1")), cfg.getRequestedProjects());
    }

    @Test
    public void testMultipleProject() {
        final HttpServletRequest request = createRequest(new String[]{"project-1", "project-3", "project-6"}, null);

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(Arrays.asList("project-1", "project-3", "project-6")), cfg.getRequestedProjects());
    }

    @Test
    public void testNonIndexedProject() {
        env.getProjects().get("project-1").setIndexed(false);
        final HttpServletRequest request = createRequest(new String[]{"project-1"}, null);

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(), cfg.getRequestedProjects());

        env.getProjects().get("project-1").setIndexed(true);
    }

    @Test
    public void testMultipleWithNonIndexedProject() {
        env.getProjects().get("project-1").setIndexed(false);
        final HttpServletRequest request = createRequest(new String[]{"project-1", "project-3", "project-6"}, null);

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(Arrays.asList("project-3", "project-6")), cfg.getRequestedProjects());

        env.getProjects().get("project-1").setIndexed(true);
    }

    @Test
    public void testSingleGroup1() {
        final HttpServletRequest request = createRequest(null, new String[]{"group-1-2-3"});

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(Arrays.asList("project-1", "project-2", "project-3")), cfg.getRequestedProjects());
    }

    @Test
    public void testSingleGroup2() {
        final HttpServletRequest request = createRequest(null, new String[]{"group-7-8-9"});

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(Arrays.asList("project-7", "project-8", "project-9")), cfg.getRequestedProjects());
    }

    @Test
    public void testMultipleGroup() {
        final HttpServletRequest request = createRequest(null, new String[]{"group-1-2-3", "group-7-8-9"});

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(Arrays.asList(
                "project-1", "project-2", "project-3",
                "project-7", "project-8", "project-9")), cfg.getRequestedProjects());
    }

    @Test
    public void testMixedGroupAndProjectAddingNewProjects() {
        final HttpServletRequest request = createRequest(new String[]{"project-1", "project-6"}, new String[]{"group-7-8-9"});

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(Arrays.asList(
                "project-1", "project-6",
                "project-7", "project-8", "project-9")), cfg.getRequestedProjects());
    }


    @Test
    public void testMixedGroupNonExistentGroupAndProjectAddingNewProjects() {
        final HttpServletRequest request = createRequest(new String[]{"project-1", "project-6"}, new String[]{"no-group", "group-7-8-9"});

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(Arrays.asList(
                "project-1", "project-6",
                "project-7", "project-8", "project-9")), cfg.getRequestedProjects());
    }

    @Test
    public void testMixedGroupAndProjectInclusion() {
        final HttpServletRequest request = createRequest(new String[]{"project-1", "project-2"}, new String[]{"group-1-2-3", "group-7-8-9"});

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(Arrays.asList(
                "project-1", "project-2", "project-3",
                "project-7", "project-8", "project-9")), cfg.getRequestedProjects());
    }

    @Test
    public void testNonIndexedInGroup() {
        env.getProjects().get("project-1").setIndexed(false);
        final HttpServletRequest request = createRequest(null, new String[]{"group-1-2-3"});

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(Arrays.asList("project-2", "project-3")), cfg.getRequestedProjects());

        env.getProjects().get("project-1").setIndexed(true);
    }

    /**
     * Assumes that there is no defaultProjects and no cookie set up.
     */
    @Test
    public void testNonExistentProject() {
        final HttpServletRequest request = createRequest(new String[]{"no-project"}, null);

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(), cfg.getRequestedProjects());
    }

    /**
     * Assumes that there is no defaultProjects and no cookie set up.
     */
    @Test
    public void testNonExistentGroup() {
        final HttpServletRequest request = createRequest(null, new String[]{"no-group"});

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new HashSet<>(), cfg.getRequestedProjects());
    }

    @Test
    public void testSelectAllProjects() {
        final HttpServletRequest request = createRequest(null, null);
        Mockito.when(request.getParameter(QueryParameters.ALL_PROJECT_SEARCH)).thenReturn("true");

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new TreeSet<>(env.getProjectNames()), cfg.getRequestedProjects());
    }

    @Test
    public void testSelectAllProjectsOverrideProjectParam() {
        final HttpServletRequest request = createRequest(new String[]{"project-1", "project-2"}, null);
        Mockito.when(request.getParameter(QueryParameters.ALL_PROJECT_SEARCH)).thenReturn("true");

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new TreeSet<>(env.getProjectNames()), cfg.getRequestedProjects());
    }

    @Test
    public void testSelectAllProjectsOverrideGroupParam() {
        final HttpServletRequest request = createRequest(null, new String[]{"group-1-2-3"});
        Mockito.when(request.getParameter(QueryParameters.ALL_PROJECT_SEARCH)).thenReturn("true");

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new TreeSet<>(env.getProjectNames()), cfg.getRequestedProjects());
    }

    @Test
    public void testSelectAllOverrideNonExistentProject() {
        final HttpServletRequest request = createRequest(new String[]{"no-project"}, null);
        Mockito.when(request.getParameter(QueryParameters.ALL_PROJECT_SEARCH)).thenReturn("true");

        final PageConfig cfg = PageConfig.get(request);
        assertEquals(new TreeSet<>(env.getProjectNames()), cfg.getRequestedProjects());
    }

    /**
     * Create a request with the specified path elements.
     *
     * @return a servlet request for the specified path
     */
    private static HttpServletRequest createRequest(final String[] projects, final String[] groups) {
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameterValues(PageConfig.PROJECT_PARAM_NAME)).thenReturn(projects);
        Mockito.when(request.getParameterValues(PageConfig.GROUP_PARAM_NAME)).thenReturn(groups);
        return request;
    }
}
