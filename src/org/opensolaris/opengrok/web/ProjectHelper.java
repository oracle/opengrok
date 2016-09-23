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
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.RepositoryInfo;

import static org.opensolaris.opengrok.web.PageConfig.OPEN_GROK_PROJECT;

/**
 * Preprocessing of projects, repositories and groups for the UI
 *
 * @author Krystof Tulinger
 */
public final class ProjectHelper {

    private static final String ATTR_NAME = "project_helper";

    private static final String PROJECT_HELPER_GROUPS = "project_helper_groups";
    private static final String PROJECT_HELPER_UNGROUPED_PROJECTS = "project_helper_ungrouped_projects";
    private static final String PROJECT_HELPER_UNGROUPED_REPOSITORIES = "project_helper_ungrouped_repositories";
    private static final String PROJECT_HELPER_GROUPED_PROJECT_GROUP = "project_helper_grouped_project_group_";
    private static final String PROJECT_HELPER_GROUPED_REPOSITORIES = "project_helper_grouped_repositories";
    private static final String PROJECT_HELPER_ALLOWED_SUBGROUP = "project_helper_allowed_subgroup";
    private static final String PROJECT_HELPER_GROUPED_REPOSITORIES_GROUP = "project_helper_grouped_repositories_group_";
    private static final String PROJECT_HELPER_GROUPED_PROJECTS = "project_helper_grouped_projects";
    private static final String PROJECT_HELPER_SUBGROUPS_OF = "project_helper_subgroups_of_";
    private static final String PROJECT_HELPER_FAVOURITE_GROUP = "project_helper_favourite_group";

    private PageConfig cfg;
    /**
     * Set of groups
     */
    private final Set<Group> groups;
    /**
     * Set of projects (not repositories) without group
     */
    private final Set<Project> projects;
    /**
     * Set of all repositories without group
     */
    private final Set<Project> repositories;
    /**
     * Set of all projects with group
     */
    private final Set<Project> all_projects = new TreeSet<>();
    /**
     * Set of all repositories with group
     */
    private final Set<Project> all_repositories = new TreeSet<>();

    private ProjectHelper(PageConfig cfg) {
        this.cfg = cfg;
        groups = new TreeSet<>(cfg.getEnv().getGroups());
        projects = new TreeSet<>();
        repositories = new TreeSet<>();

        populateGroups();
    }

    /**
     * Object of project helper should be ONLY obtained by calling
     * PageConfig#getProjectHelper.
     *
     * @param cfg current page config
     * @return instance of ProjectHelper
     * @see PageConfig#getProjectHelper()
     */
    public static ProjectHelper getInstance(PageConfig cfg) {
        ProjectHelper instance = (ProjectHelper) cfg.getRequestAttribute(ATTR_NAME);
        if (instance == null) {
            instance = new ProjectHelper(cfg);
            cfg.setRequestAttribute(ATTR_NAME, instance);
        }
        return instance;
    }

    /**
     * Get repository info for particular project
     *
     * @param p Project
     * @return List of repository info or empty List if no info is found
     */
    public List<RepositoryInfo> getRepositoryInfo(Project p) {
        if (!cfg.isAllowed(p)) {
            return new ArrayList<>();
        }
        Map<Project, List<RepositoryInfo>> map = cfg.getEnv().getProjectRepositoriesMap();
        List<RepositoryInfo> info = map.get(p);
        return info == null ? new ArrayList<>() : info;
    }

    /**
     * Generates ungrouped projects and repositories.
     */
    private void populateGroups() {
        groups.addAll(cfg.getEnv().getGroups());
        for (Project project : cfg.getEnv().getProjects()) {
            // filterProjects only groups which match project's description
            Set<Group> copy = new TreeSet<>(groups);
            copy.removeIf(new Predicate<Group>() {
                @Override
                public boolean test(Group g) {
                    return !g.match(project);
                }
            });

            // if no group matches the project, add it to not-grouped projects
            if (copy.isEmpty()) {
                if (cfg.getEnv().getProjectRepositoriesMap().get(project) == null) {
                    projects.add(project);
                } else {
                    repositories.add(project);
                }
            }
        }

        // populate all grouped
        for (Group g : getGroups()) {
            all_projects.addAll(getProjects(g));
            all_repositories.addAll(getRepositories(g));
        }
    }

    /**
     * Filters set of projects based on the authorizator options.
     *
     * @param p set of projects
     * @return filtered set of projects
     */
    private Set<Project> filterProjects(Set<Project> p) {
        Set<Project> repos = new TreeSet<>(p);
        repos.removeIf(new Predicate<Project>() {
            @Override
            public boolean test(Project t) {
                return !cfg.isAllowed(t);
            }
        });
        return repos;
    }

    /**
     * Filters set of groups based on the authorizator options.
     *
     * @param p set of groups
     * @return filtered set of groups
     */
    private Set<Group> filterGroups(Set<Group> p) {
        Set<Group> grps = new TreeSet<>(p);
        grps.removeIf(new Predicate<Group>() {
            @Override
            public boolean test(Group t) {
                return !(cfg.isAllowed(t) || hasAllowedSubgroup(t));
            }
        });
        return grps;
    }

    /**
     * Filters and saves the original set of projects into request's attribute.
     *
     * @param name attribute name
     * @param original original set
     * @return filtered set
     */
    @SuppressWarnings(value = "unchecked")
    private Set<Project> cacheProjects(String name, Set<Project> original) {
        Set<Project> p = (Set<Project>) cfg.getRequestAttribute(name);
        if (p == null) {
            p = filterProjects(original);
            cfg.setRequestAttribute(name, p);
        }
        return p;
    }

    /**
     * Filters and saves the original set of groups into request's attribute.
     *
     * @param name attribute name
     * @param original original set
     * @return filtered set
     */
    @SuppressWarnings(value = "unchecked")
    private Set<Group> cacheGroups(String name, Set<Group> original) {
        Set<Group> p = (Set<Group>) cfg.getRequestAttribute(name);
        if (p == null) {
            p = filterGroups(original);
            cfg.setRequestAttribute(name, p);
        }
        return p;
    }

    /**
     * @return filtered groups
     */
    public Set<Group> getGroups() {
        return cacheGroups(PROJECT_HELPER_GROUPS, groups);
    }

    /**
     * @return filtered ungrouped projects
     */
    public Set<Project> getProjects() {
        return cacheProjects(PROJECT_HELPER_UNGROUPED_PROJECTS, projects);
    }

    /**
     * @return filtered ungrouped repositories
     */
    public Set<Project> getRepositories() {
        return cacheProjects(PROJECT_HELPER_UNGROUPED_REPOSITORIES, repositories);
    }

    /**
     * @param g group
     * @return filtered group's projects
     */
    public Set<Project> getProjects(Group g) {
        if (!cfg.isAllowed(g)) {
            return new TreeSet<>();
        }
        return cacheProjects(PROJECT_HELPER_GROUPED_PROJECT_GROUP + g.getName().toLowerCase(), g.getProjects());
    }
    
    /**
     * @param g group
     * @return filtered group's repositories
     */
    public Set<Project> getRepositories(Group g) {
        if (!cfg.isAllowed(g)) {
            return new TreeSet<>();
        }
        return cacheProjects(PROJECT_HELPER_GROUPED_REPOSITORIES_GROUP + g.getName().toLowerCase(), g.getRepositories());
    }

    /**
     * @return filtered grouped projects
     */
    public Set<Project> getGroupedProjects() {
        return cacheProjects(PROJECT_HELPER_GROUPED_PROJECTS, all_projects);
    }

    /**
     * @return filtered grouped repositories
     */
    public Set<Project> getGroupedRepositories() {
        return cacheProjects(PROJECT_HELPER_GROUPED_REPOSITORIES, all_repositories);
    }

    /**
     * @see #getProjects()
     * @return filtered ungrouped projects
     */
    public Set<Project> getUngroupedProjects() {
        return cacheProjects(PROJECT_HELPER_UNGROUPED_PROJECTS, projects);
    }

    /**
     * @see #getRepositories()
     * @return filtered ungrouped projects
     */
    public Set<Project> getUngroupedRepositories() {
        return cacheProjects(PROJECT_HELPER_UNGROUPED_REPOSITORIES, repositories);
    }

    /**
     * @return filtered projects and repositories
     */
    public Set<Project> getAllGrouped() {
        return mergeProjects(getGroupedProjects(), getGroupedRepositories());
    }

    /**
     * @param g group
     * @return filtered set of all projects and repositories in group g
     */
    public Set<Project> getAllGrouped(Group g) {
        if (!cfg.isAllowed(g)) {
            return new TreeSet<>();
        }
        return mergeProjects(filterProjects(g.getProjects()), filterProjects(g.getRepositories()));
    }

    /**
     * @return filtered set of all projects and repositories without group
     */
    public Set<Project> getAllUngrouped() {
        return mergeProjects(getUngroupedProjects(), getUngroupedRepositories());
    }

    /**
     * @return filtered set of all projects and repositories no matter if
     * grouped or ungrouped
     */
    public Set<Project> getAllProjects() {
        return mergeProjects(getAllUngrouped(), getAllGrouped());
    }

    /**
     * @param g group
     * @return filtered set of subgroups
     */
    public Set<Group> getSubgroups(Group g) {
        if (!cfg.isAllowed(g)) {
            return new TreeSet<>();
        }
        return cacheGroups(PROJECT_HELPER_SUBGROUPS_OF + g.getName().toLowerCase(), g.getSubgroups());
    }

    /**
     * Checks if given group contains a subgroup which is allowed by the
     * AuthorizationFramework.
     *
     * This should be used for deciding if this group should be written in the
     * group hierarchy in the resulting html because it contains other allowed
     * groups.
     *
     * @param group group
     * @return true it it has an allowed subgroup
     */
    @SuppressWarnings(value = "unchecked")
    public boolean hasAllowedSubgroup(Group group) {
        Boolean val;
        Map<String, Boolean> p = (Map<String, Boolean>) cfg.getRequestAttribute(PROJECT_HELPER_ALLOWED_SUBGROUP);
        if (p == null) {
            p = new TreeMap<String, Boolean>();
            cfg.setRequestAttribute(PROJECT_HELPER_ALLOWED_SUBGROUP, p);
        }
        val = p.get(group.getName());
        if (val == null) {
            val = cfg.isAllowed(group);
            val = val && !filterGroups(group.getDescendants()).isEmpty();
            p = (Map<String, Boolean>) cfg.getRequestAttribute(PROJECT_HELPER_ALLOWED_SUBGROUP);
            p.put(group.getName(), val);
        }
        cfg.setRequestAttribute(PROJECT_HELPER_ALLOWED_SUBGROUP, p);
        return val;
    }

    /**
     * Checks if given group contains a favourite project.
     *
     * Favourite project is a project which is contained in the OpenGrokProject
     * cookie, i. e. it has been searched or viewed by the user.
     *
     * This should by used to determine if this group should be displayed
     * expanded or rolled up.
     *
     * @param group group
     * @return true if it has favourite project
     */
    @SuppressWarnings(value = "unchecked")
    public boolean hasFavourite(Group group) {
        Boolean val;
        Map<String, Boolean> p = (Map<String, Boolean>) cfg.getRequestAttribute(PROJECT_HELPER_FAVOURITE_GROUP);
        if (p == null) {
            p = new TreeMap<String, Boolean>();
            cfg.setRequestAttribute(PROJECT_HELPER_FAVOURITE_GROUP, p);
        }
        val = p.get(group.getName());
        if (val == null) {
            Set<Project> favourite = getAllGrouped();
            favourite.removeIf(new Predicate<Project>() {
                @Override
                public boolean test(Project t) {
                    // project is favourite
                    if (!isFavourite(t)) {
                        return true;
                    }
                    // project is contained in group repositories
                    if (getRepositories(group).contains(t)) {
                        return false;
                    }
                    // project is contained in group projects
                    if (getProjects(group).contains(t)) {
                        return false;
                    }
                    // project is contained in subgroup's repositories and projects
                    for (Group g : filterGroups(group.getDescendants())) {
                        if (getProjects(g).contains(t)) {
                            return false;
                        }
                        if (getRepositories(g).contains(t)) {
                            return false;
                        }
                    }
                    return true;
                }
            });
            val = !favourite.isEmpty();
            p.put(group.getName(), val);
        }
        cfg.setRequestAttribute(PROJECT_HELPER_FAVOURITE_GROUP, p);
        return val;
    }
    
    /**
     * Checks if the project is a favourite project
     *
     * @param project project
     * @return true if it is favourite
     */
    public boolean isFavourite(Project project) {
        return cfg.getCookieVals(OPEN_GROK_PROJECT).contains(project.getDescription());
    }

    /**
     * Checks if there is a favourite project in ungrouped projects.
     *
     * This should by used to determine if this 'other' section should be
     * displayed expanded or rolled up.
     *
     * @return true if there is
     */
    public boolean hasUngroupedFavourite() {
        for (Project p : getAllUngrouped()) {
            if (isFavourite(p)) {
                return true;
            }
        }
        return false;
    }

    private static Set<Project> mergeProjects(Set<Project> p1, Set<Project> p2) {
        Set<Project> set = new TreeSet<Project>();
        set.addAll(p1);
        set.addAll(p2);
        return set;
    }

    public static void cleanup(PageConfig cfg) {
        if (cfg != null) {
            ProjectHelper helper = (ProjectHelper) cfg.getRequestAttribute(ATTR_NAME);
            if (helper == null) {
                return;
            }
            cfg.removeAttribute(ATTR_NAME);
            helper.cfg = null;
        }
    }
}
