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
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import jakarta.servlet.http.Cookie;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.web.DummyHttpServletRequest;

public class ProjectHelperExtendedTest extends ProjectHelperTestBase {

    @BeforeClass
    public static void setUpClass() {
        ProjectHelperTest.setUpClass();

        List<Group> grps = new ArrayList<>(env.getGroups());
        Map<String, Project> projects = env.getProjects();
        List<RepositoryInfo> rps = env.getRepositories();
        Map<Project, List<RepositoryInfo>> map = getRepositoriesMap();

        /**
         * Extend the original groups with some subgroups - structure should be
         * now like this.
         *
         *      allowed_group_2                 group_1
         *     /               \               /       \
         * group_0       allowed_group_3  group_0    allowed_group_3
         */
        Group.getByName("allowed_group_2").addGroup(Group.getByName("group_0"));
        Group.getByName("allowed_group_2").addGroup(Group.getByName("allowed_group_3"));
        Group.getByName("group_1").addGroup(Group.getByName("group_0"));
        Group.getByName("group_1").addGroup(Group.getByName("allowed_group_3"));

        setRepositoriesMap(map);
        env.setProjects(projects);
        env.setGroups(new TreeSet<>(grps));
        env.setRepositories(rps);
    }

    private void setupPageConfigRequest(final String cookie) {
        cfg = PageConfig.get(new DummyHttpServletRequest() {
            @Override
            public Cookie[] getCookies() {
                Cookie[] ret = new Cookie[1];
                ret[0] = new Cookie("OpenGrokProject", cookie);
                return ret;
            }
        });
        helper = cfg.getProjectHelper();
    }

    protected static Project createProject(String name) {
        Project p = new Project(name);
        p.setIndexed(true);
        return p;
    }

    private Group getAllowedGroupWithSubgroups() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups()) {
            if (g.getName().startsWith("allowed") && !g.getSubgroups().isEmpty()) {
                return g;
            }
        }
        return null;
    }

    private Group getAllowedGroupWithoutSubgroups() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups()) {
            if (g.getName().startsWith("allowed") && g.getSubgroups().isEmpty()) {
                return g;
            }
        }
        return null;
    }

    private Group getUnAllowedGroupWithSubgroups() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups()) {
            if (g.getName().startsWith("group") && !g.getSubgroups().isEmpty()) {
                return g;
            }
        }
        return null;
    }

    private Group getUnAllowedGroupWithoutSubgroups() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups()) {
            if (g.getName().startsWith("group") && g.getSubgroups().isEmpty()) {
                return g;
            }
        }
        return null;
    }

    @Test
    public void testGetAllowedGroupSubgroups() {
        Set<Group> result = helper.getSubgroups(getAllowedGroupWithSubgroups());
        Assert.assertEquals(1, result.size());
        for (Group p : result) {
            Assert.assertTrue(p.getName().startsWith("allowed_"));
        }
    }

    @Test
    public void testGetUnAllowedGroupSubgroups() {
        Set<Group> result = helper.getSubgroups(getUnAllowedGroupWithSubgroups());
        Assert.assertEquals(0, result.size());
    }

    @Test
    public void testHasAllowedSubgroupAllowedSubgroups() {
        Group g = getAllowedGroupWithSubgroups();
        Assert.assertTrue(helper.hasAllowedSubgroup(g));
    }

    @Test
    public void testHasAllowedSubgroupUnAllowedSubgroups() {
        Group g = getUnAllowedGroupWithSubgroups();
        Assert.assertFalse(helper.hasAllowedSubgroup(g));
    }

    @Test
    public void testHasAllowedSubgroupAllowedNoSubgroups() {
        Group g = getAllowedGroupWithoutSubgroups();
        Assert.assertFalse(helper.hasAllowedSubgroup(g));
    }

    @Test
    public void testHasAllowedSubgroupUnAllowedNoSubgroups() {
        Group g = getUnAllowedGroupWithoutSubgroups();
        Assert.assertFalse(helper.hasAllowedSubgroup(g));
    }

    @Test
    public void testIsFavourite() {
        setupPageConfigRequest("grouped_project_0_1,"
                + "grouped_repository_2_2,"
                + "allowed_grouped_repository_0_2,"
                + "allowed_grouped_project_1_2,"
                + "allowed_ungrouped_project_2_1,"
                + "allowed_ungrouped_repository_2_1,"
                + "ungrouped_repository_1_1,"
                + "ungrouped_project_0_1");

        Assert.assertTrue(helper.isFavourite(createProject("grouped_project_0_1")));
        Assert.assertTrue(helper.isFavourite(createProject("grouped_repository_2_2")));
        Assert.assertTrue(helper.isFavourite(createProject("allowed_grouped_repository_0_2")));
        Assert.assertTrue(helper.isFavourite(createProject("allowed_grouped_project_1_2")));
        Assert.assertTrue(helper.isFavourite(createProject("allowed_ungrouped_project_2_1")));
        Assert.assertTrue(helper.isFavourite(createProject("allowed_ungrouped_repository_2_1")));
        Assert.assertTrue(helper.isFavourite(createProject("ungrouped_repository_1_1")));
        Assert.assertTrue(helper.isFavourite(createProject("ungrouped_project_0_1")));

        Assert.assertFalse(helper.isFavourite(createProject("uknown")));
        Assert.assertFalse(helper.isFavourite(createProject("ungrouped_project_0_2")));
        Assert.assertFalse(helper.isFavourite(createProject("ungrouped_epository_1_1")));
        Assert.assertFalse(helper.isFavourite(createProject("allowed_grouped_repository_2_1")));
        Assert.assertFalse(helper.isFavourite(createProject("grouped_project__0_1")));
        Assert.assertFalse(helper.isFavourite(createProject("gd6sf8g718fd7gsd68dfg")));
        Assert.assertFalse(helper.isFavourite(createProject("Chuck Norris")));
    }

    @Test
    public void testHasUngroupedFavouritePositive() {
        setupPageConfigRequest("grouped_project_0_1,"
                + "grouped_repository_2_2,"
                + "allowed_grouped_repository_0_2,"
                + "allowed_grouped_project_1_2,"
                + "allowed_ungrouped_repository_2_1,"
                + "ungrouped_repository_1_1,"
                + "ungrouped_project_0_1");
        Assert.assertTrue(helper.hasUngroupedFavourite());
    }

    @Test
    public void testHasUngroupedFavouriteNegative() {
        setupPageConfigRequest("grouped_project_0_1,"
                + "grouped_repository_2_2,"
                + "allowed_grouped_repository_0_2,"
                + "allowed_grouped_project_1_2,"
                + "ungrouped_repository_1_1,"
                + "ungrouped_project_0_1");
        Assert.assertFalse(helper.hasUngroupedFavourite());
    }

    @Test
    public void testHasFavourite() {
        String[] cookie = new String[] {
                "grouped_project_2_1",
                "allowed_grouped_project_2_1",
                "ungrouped_project_2_1",
                "uknown",
                "allowed_grouped_project_0_1",
                "grouped_project_0_1"};
        boolean[] exp = new boolean[] {
                false,
                true,
                false,
                false,
                false,
                false};
        Group[] groups = new Group[] {
                Group.getByName("allowed_group_2"),
                Group.getByName("allowed_group_2"),
                Group.getByName("allowed_group_2"),
                Group.getByName("allowed_group_2"),
                Group.getByName("group_0"),
                Group.getByName("group_0")};

        Assert.assertTrue(groups.length == exp.length && exp.length == cookie.length);

        for (int i = 0; i < exp.length; i++) {
            setupPageConfigRequest(cookie[i]);
            Assert.assertEquals(exp[i], helper.hasFavourite(groups[i]));
        }
    }

}
