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
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.RepoRepository;
import org.opengrok.indexer.history.RepositoryInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectHelperTest extends ProjectHelperTestBase {

    /**
     * Test of getInstance method, of class ProjectHelper.
     */
    @Test
    public void testGetInstance() {
        ProjectHelper result = ProjectHelper.getInstance(cfg);
        assertNotNull(result, "Project helper should not be null");
        assertSame(result.getClass(), ProjectHelper.class);
    }

    /**
     * Test if projects and groups are always reloaded fully from the env.
     *
     * This ensures that when the RuntimeEnvironment changes that it also
     * updates the projects in the UI.
     */
    @Test
    public void testSynchronization() {
        HashMap<String, Project> oldProjects = new HashMap<>(env.getProjects());
        List<RepositoryInfo> oldRepositories = new ArrayList<>(env.getRepositories());
        Set<Group> oldGroups = new TreeSet<>(env.getGroups());
        Map<Project, List<RepositoryInfo>> oldMap = new TreeMap<>(getRepositoriesMap());
        env.getAuthorizationFramework().removeAll();
        env.setSourceRoot("/src"); // needed for setDirectoryName() below

        cfg = PageConfig.get(getRequest());
        helper = cfg.getProjectHelper();

        // basic setup
        assertEquals(40, env.getProjects().size(), "There should be 40 env projects");
        assertEquals(20, env.getRepositories().size(), "There should be 20 env repositories");
        assertEquals(4, env.getGroups().size(), "There should be 4 env groups");

        assertEquals(8, helper.getAllUngrouped().size(), "There are 8 ungrouped projects");
        assertEquals(40, helper.getAllProjects().size(), "There are 40 projects");
        assertEquals(4, helper.getRepositories().size(), "There are 4 projects");
        assertEquals(4, helper.getGroups().size(), "There are 4 groups");

        // project
        Project p = new Project("some random name not in any group");
        p.setIndexed(true);

        // group
        Group g = new Group("some random name of a group");

        // repository
        Project repo = new Project("some random name not in any other group");
        repo.setIndexed(true);

        RepositoryInfo info = new RepoRepository();
        info.setParent(repo.getName());
        info.setDirectoryName(new File("/foo"));

        List<RepositoryInfo> infos = getRepositoriesMap().get(repo);
        if (infos == null) {
            infos = new ArrayList<>();
        }
        infos.add(info);

        getRepositoriesMap().put(repo, infos);
        env.getRepositories().add(info);
        env.getProjects().put("foo", p);
        env.getProjects().put("bar", repo);
        env.getGroups().add(g);

        assertEquals(42, env.getProjects().size());
        assertEquals(21, env.getRepositories().size());
        assertEquals(5, env.getGroups().size());

        // simulate another request
        cfg = PageConfig.get(getRequest());
        helper = cfg.getProjectHelper();

        // check for updates
        assertEquals(10, helper.getAllUngrouped().size(), "The helper state should refresh");
        assertEquals(42, helper.getAllProjects().size(), "The helper state should refresh");
        assertEquals(5, helper.getRepositories().size(), "The helper state should refresh");
        assertEquals(5, helper.getGroups().size(), "The helper state should refresh");

        setRepositoriesMap(oldMap);
        env.setProjects(oldProjects);
        env.setRepositories(oldRepositories);
        env.setGroups(oldGroups);
    }

    /**
     * Test of getRepositoryInfo method, of class ProjectHelper.
     */
    @Test
    public void testUnAllowedGetRepositoryInfo() {
        Project p = new Project("repository_2_1");
        p.setIndexed(true);
        List<RepositoryInfo> result = helper.getRepositoryInfo(p);
        assertEquals(0, result.size(), "this project is not allowed");
    }

    /**
     * Test of getRepositoryInfo method, of class ProjectHelper.
     */
    @Test
    public void testAllowedGetRepositoryInfo() {
        Project p = new Project("allowed_grouped_repository_0_1");
        p.setIndexed(true);
        List<RepositoryInfo> result = helper.getRepositoryInfo(p);
        assertEquals(1, result.size());
        assertEquals("allowed_grouped_repository_0_1_" + 0, result.get(0).getParent());
    }

    /**
     * Test of getGroups method, of class ProjectHelper.
     */
    @Test
    public void testGetAllowedGroups() {
        Set<Group> result = helper.getGroups();
        assertEquals(2, result.size());
        for (Group g : result) {
            assertTrue(g.getName().startsWith("allowed_"));
        }
    }

    /**
     * Test of getProjects method, of class ProjectHelper.
     */
    @Test
    public void testGetAllowedProjects() {
        Set<Project> result = helper.getProjects();
        assertEquals(2, result.size());
        for (Project p : result) {
            assertTrue(p.getName().startsWith("allowed_"));
        }
    }

    /**
     * Test of getRepositories method, of class ProjectHelper.
     */
    @Test
    public void testGetRepositories() {
        Set<Project> result = helper.getRepositories();
        assertEquals(2, result.size());
        for (Project p : result) {
            assertTrue(p.getName().startsWith("allowed_"));
        }
    }

    /**
     * Test of getProjects method, of class ProjectHelper.
     */
    @Test
    public void testGetProjectsAllowedGroup() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups()) {
            if (g.getName().startsWith("allowed_group_0")) {
                Set<Project> result = helper.getProjects(g);
                assertEquals(2, result.size());
                for (Project p : result) {
                    assertTrue(p.getName().startsWith("allowed_"));
                }
            }

        }
    }

    /**
     * Test of getProjects method, of class ProjectHelper.
     */
    @Test
    public void testGetProjectsUnAllowedGroup() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups()) {
            if (g.getName().startsWith("group_0")) {
                assertEquals(0, helper.getProjects(g).size());
                break;
            }

        }
    }

    /**
     * Test of getRepositories method, of class ProjectHelper.
     */
    @Test
    public void testGetRepositoriesAllowedGroup() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups()) {
            if (g.getName().startsWith("allowed_group_0")) {
                Set<Project> result = helper.getRepositories(g);
                assertEquals(2, result.size());
                for (Project p : result) {
                    assertTrue(p.getName().startsWith("allowed_"));
                }
            }

        }
    }

    /**
     * Test of getRepositories method, of class ProjectHelper.
     */
    @Test
    public void testGetRepositoriesUnAllowedGroup() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups()) {
            if (g.getName().startsWith("group_0")) {
                assertEquals(0, helper.getRepositories(g).size());
                break;
            }

        }
    }

    /**
     * Test of getGroupedProjects method, of class ProjectHelper.
     */
    @Test
    public void testGetGroupedProjects() {
        Set<Project> result = helper.getGroupedProjects();
        assertEquals(4, result.size());
        for (Project p : result) {
            assertTrue(p.getName().startsWith("allowed_"));
        }
    }

    /**
     * Test of getGroupedRepositories method, of class ProjectHelper.
     */
    @Test
    public void testGetGroupedRepositories() {
        Set<Project> result = helper.getGroupedRepositories();
        assertEquals(4, result.size());
        for (Project p : result) {
            assertTrue(p.getName().startsWith("allowed_"));
        }
    }

    /**
     * Test of getUngroupedProjects method, of class ProjectHelper.
     */
    @Test
    public void testGetUngroupedProjects() {
        Set<Project> result = helper.getUngroupedProjects();
        assertEquals(2, result.size());
        for (Project p : result) {
            assertTrue(p.getName().startsWith("allowed_"));
        }
    }

    /**
     * Test of getUngroupedRepositories method, of class ProjectHelper.
     */
    @Test
    public void testGetUngroupedRepositories() {
        Set<Project> result = helper.getUngroupedRepositories();
        assertEquals(2, result.size());
        for (Project p : result) {
            assertTrue(p.getName().startsWith("allowed_"));
        }
    }

    /**
     * Test of getAllGrouped method, of class ProjectHelper.
     */
    @Test
    public void testGetAllGrouped() {
        Set<Project> result = helper.getAllGrouped();
        assertEquals(8, result.size());
        for (Project p : result) {
            assertTrue(p.getName().startsWith("allowed_"));
        }
    }

    /**
     * Test of getAllGrouped method, of class ProjectHelper.
     */
    @Test
    public void testGetAllGroupedAllowedGroup() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups()) {
            if (g.getName().startsWith("allowed_group_0")) {
                Set<Project> result = helper.getAllGrouped(g);
                assertEquals(4, result.size());
                for (Project p : result) {
                    assertTrue(p.getName().startsWith("allowed_"));
                }
            }

        }
    }

    @Test
    public void testGetAllGroupedUnAllowedGroup() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups()) {
            if (g.getName().startsWith("group_0")) {
                assertEquals(0, helper.getAllGrouped(g).size());
                break;
            }
        }
    }

    /**
     * Test of getAllUngrouped method, of class ProjectHelper.
     */
    @Test
    public void testGetAllUngrouped() {
        Set<Project> result = helper.getAllUngrouped();
        assertEquals(4, result.size());
        for (Project p : result) {
            assertTrue(p.getName().startsWith("allowed_"));
        }
    }

    /**
     * Test of getAllProjects method, of class ProjectHelper.
     */
    @Test
    public void testGetAllProjects() {
        Set<Project> result = helper.getAllProjects();
        assertEquals(12, result.size());
        for (Project p : result) {
            assertTrue(p.getName().startsWith("allowed_"));
        }
    }
}
