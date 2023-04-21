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
 */
package org.opengrok.web;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.web.DummyHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectHelperExtendedTest extends ProjectHelperTestBase {

    @BeforeAll
    public static void setUpClass() {
        ProjectHelperTest.setUpClass();

        Map<String, Group> grps = env.getGroups();
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
        env.setGroups(grps);
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
        for (Group g : RuntimeEnvironment.getInstance().getGroups().values()) {
            if (g.getName().startsWith("allowed") && !g.getSubgroups().isEmpty()) {
                return g;
            }
        }
        return null;
    }

    private Group getAllowedGroupWithoutSubgroups() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups().values()) {
            if (g.getName().startsWith("allowed") && g.getSubgroups().isEmpty()) {
                return g;
            }
        }
        return null;
    }

    private Group getUnAllowedGroupWithSubgroups() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups().values()) {
            if (g.getName().startsWith("group") && !g.getSubgroups().isEmpty()) {
                return g;
            }
        }
        return null;
    }

    private Group getUnAllowedGroupWithoutSubgroups() {
        for (Group g : RuntimeEnvironment.getInstance().getGroups().values()) {
            if (g.getName().startsWith("group") && g.getSubgroups().isEmpty()) {
                return g;
            }
        }
        return null;
    }

    @Test
    public void testGetAllowedGroupSubgroups() {
        Set<Group> result = helper.getSubgroups(getAllowedGroupWithSubgroups());
        assertEquals(1, result.size());
        for (Group p : result) {
            assertTrue(p.getName().startsWith("allowed_"));
        }
    }

    @Test
    public void testGetUnAllowedGroupSubgroups() {
        Set<Group> result = helper.getSubgroups(getUnAllowedGroupWithSubgroups());
        assertEquals(0, result.size());
    }

    @Test
    public void testHasAllowedSubgroupAllowedSubgroups() {
        Group g = getAllowedGroupWithSubgroups();
        assertTrue(helper.hasAllowedSubgroup(g));
    }

    @Test
    public void testHasAllowedSubgroupUnAllowedSubgroups() {
        Group g = getUnAllowedGroupWithSubgroups();
        assertFalse(helper.hasAllowedSubgroup(g));
    }

    @Test
    public void testHasAllowedSubgroupAllowedNoSubgroups() {
        Group g = getAllowedGroupWithoutSubgroups();
        assertFalse(helper.hasAllowedSubgroup(g));
    }

    @Test
    public void testHasAllowedSubgroupUnAllowedNoSubgroups() {
        Group g = getUnAllowedGroupWithoutSubgroups();
        assertFalse(helper.hasAllowedSubgroup(g));
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

        assertTrue(helper.isFavourite(createProject("grouped_project_0_1")));
        assertTrue(helper.isFavourite(createProject("grouped_repository_2_2")));
        assertTrue(helper.isFavourite(createProject("allowed_grouped_repository_0_2")));
        assertTrue(helper.isFavourite(createProject("allowed_grouped_project_1_2")));
        assertTrue(helper.isFavourite(createProject("allowed_ungrouped_project_2_1")));
        assertTrue(helper.isFavourite(createProject("allowed_ungrouped_repository_2_1")));
        assertTrue(helper.isFavourite(createProject("ungrouped_repository_1_1")));
        assertTrue(helper.isFavourite(createProject("ungrouped_project_0_1")));

        assertFalse(helper.isFavourite(createProject("uknown")));
        assertFalse(helper.isFavourite(createProject("ungrouped_project_0_2")));
        assertFalse(helper.isFavourite(createProject("ungrouped_epository_1_1")));
        assertFalse(helper.isFavourite(createProject("allowed_grouped_repository_2_1")));
        assertFalse(helper.isFavourite(createProject("grouped_project__0_1")));
        assertFalse(helper.isFavourite(createProject("gd6sf8g718fd7gsd68dfg")));
        assertFalse(helper.isFavourite(createProject("Chuck Norris")));
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
        assertTrue(helper.hasUngroupedFavourite());
    }

    @Test
    public void testHasUngroupedFavouriteNegative() {
        setupPageConfigRequest("grouped_project_0_1,"
                + "grouped_repository_2_2,"
                + "allowed_grouped_repository_0_2,"
                + "allowed_grouped_project_1_2,"
                + "ungrouped_repository_1_1,"
                + "ungrouped_project_0_1");
        assertFalse(helper.hasUngroupedFavourite());
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

        assertTrue(groups.length == exp.length && exp.length == cookie.length);

        for (int i = 0; i < exp.length; i++) {
            setupPageConfigRequest(cookie[i]);
            assertEquals(exp[i], helper.hasFavourite(groups[i]));
        }
    }

}
