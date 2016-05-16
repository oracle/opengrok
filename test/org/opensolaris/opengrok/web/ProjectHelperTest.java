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
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.RepositoryInfo;

public class ProjectHelperTest extends ProjectHelperTestBase {

    /**
     * Test of getInstance method, of class ProjectHelper.
     */
    @Test
    public void testGetInstance() {
        ProjectHelper result = ProjectHelper.getInstance(cfg);
        Assert.assertNotNull("Project helper should not be null", result);
        Assert.assertSame(result.getClass(), ProjectHelper.class);
    }

    /**
     * Test of getRepositoryInfo method, of class ProjectHelper.
     */
    @Test
    public void testUnAllowedGetRepositoryInfo() {
        Project p = new Project();
        p.setDescription("repository_2_1");
        List<RepositoryInfo> result = helper.getRepositoryInfo(p);
        Assert.assertEquals("this project is not allowed", 0, result.size());
    }

    /**
     * Test of getRepositoryInfo method, of class ProjectHelper.
     */
    @Test
    public void testAllowedGetRepositoryInfo() {
        Project p = new Project();
        p.setDescription("allowed_grouped_repository_0_1");
        List<RepositoryInfo> result = helper.getRepositoryInfo(p);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("allowed_grouped_repository_0_1_" + 0, result.get(0).getParent());
    }

    /**
     * Test of getGroups method, of class ProjectHelper.
     */
    @Test
    public void testGetAllowedGroups() {
        Set<Group> result = helper.getGroups();
        Assert.assertEquals(2, result.size());
        for (Group g : result) {
            Assert.assertTrue(g.getName().startsWith("allowed_"));
        }
    }

    /**
     * Test of getProjects method, of class ProjectHelper.
     */
    @Test
    public void testGetAllowedProjects() {
        Set<Project> result = helper.getProjects();
        Assert.assertEquals(2, result.size());
        for (Project p : result) {
            Assert.assertTrue(p.getDescription().startsWith("allowed_"));
        }
    }

    /**
     * Test of getRepositories method, of class ProjectHelper.
     */
    @Test
    public void testGetRepositories() {
        Set<Project> result = helper.getRepositories();
        Assert.assertEquals(2, result.size());
        for (Project p : result) {
            Assert.assertTrue(p.getDescription().startsWith("allowed_"));
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
                Assert.assertEquals(2, result.size());
                for (Project p : result) {
                    Assert.assertTrue(p.getDescription().startsWith("allowed_"));
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
                Assert.assertEquals(0, helper.getProjects(g).size());
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
                Assert.assertEquals(2, result.size());
                for (Project p : result) {
                    Assert.assertTrue(p.getDescription().startsWith("allowed_"));
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
                Assert.assertEquals(0, helper.getRepositories(g).size());
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
        Assert.assertEquals(4, result.size());
        for (Project p : result) {
            Assert.assertTrue(p.getDescription().startsWith("allowed_"));
        }
    }

    /**
     * Test of getGroupedRepositories method, of class ProjectHelper.
     */
    @Test
    public void testGetGroupedRepositories() {
        Set<Project> result = helper.getGroupedRepositories();
        Assert.assertEquals(4, result.size());
        for (Project p : result) {
            Assert.assertTrue(p.getDescription().startsWith("allowed_"));
        }
    }

    /**
     * Test of getUngroupedProjects method, of class ProjectHelper.
     */
    @Test
    public void testGetUngroupedProjects() {
        Set<Project> result = helper.getUngroupedProjects();
        Assert.assertEquals(2, result.size());
        for (Project p : result) {
            Assert.assertTrue(p.getDescription().startsWith("allowed_"));
        }
    }

    /**
     * Test of getUngroupedRepositories method, of class ProjectHelper.
     */
    @Test
    public void testGetUngroupedRepositories() {
        Set<Project> result = helper.getUngroupedRepositories();
        Assert.assertEquals(2, result.size());
        for (Project p : result) {
            Assert.assertTrue(p.getDescription().startsWith("allowed_"));
        }
    }

    /**
     * Test of getAllGrouped method, of class ProjectHelper.
     */
    @Test
    public void testGetAllGrouped() {
        Set<Project> result = helper.getAllGrouped();
        Assert.assertEquals(8, result.size());
        for (Project p : result) {
            Assert.assertTrue(p.getDescription().startsWith("allowed_"));
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
                Assert.assertEquals(4, result.size());
                for (Project p : result) {
                    Assert.assertTrue(p.getDescription().startsWith("allowed_"));
                }
            }

        }
    }

    @Test
    public void testGetAllGroupedUnAllowedGroup() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups()) {
            if (g.getName().startsWith("group_0")) {
                Assert.assertEquals(0, helper.getAllGrouped(g).size());
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
        Assert.assertEquals(4, result.size());
        for (Project p : result) {
            Assert.assertTrue(p.getDescription().startsWith("allowed_"));
        }
    }

    /**
     * Test of getAllProjects method, of class ProjectHelper.
     */
    @Test
    public void testGetAllProjects() {
        Set<Project> result = helper.getAllProjects();
        Assert.assertEquals(12, result.size());
        for (Project p : result) {
            Assert.assertTrue(p.getDescription().startsWith("allowed_"));
        }
    }
}
