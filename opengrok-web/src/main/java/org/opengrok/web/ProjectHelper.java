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
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
 */
package org.opengrok.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.history.RepositoryInfo;

import static org.opengrok.web.PageConfig.OPEN_GROK_PROJECT;

/**
 * Preprocessing of projects, repositories and groups for the UI.
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

    private static final Comparator<RepositoryInfo> REPOSITORY_NAME_COMPARATOR = Comparator.comparing(RepositoryInfo::getDirectoryName);

    private PageConfig cfg;
    /**
     * Set of groups.
     */
    private final Set<Group> groups;
    /**
     * Set of projects (not repositories) without group.
     */
    private final Set<Project> ungroupedProjects;
    /**
     * Set of all repositories without group.
     */
    private final Set<Project> ungroupedRepositories;
    /**
     * Set of all projects with group.
     */
    private final Set<Project> allProjects = new TreeSet<>();
    /**
     * Set of all repositories with group.
     */
    private final Set<Project> allRepositories = new TreeSet<>();

    private ProjectHelper(PageConfig cfg) {
        this.cfg = cfg;
        groups = new TreeSet<>(cfg.getEnv().getGroups().values());
        ungroupedProjects = new TreeSet<>();
        ungroupedRepositories = new TreeSet<>();

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
     * Get repository info list for particular project. A copy of the list is
     * returned always to allow concurrent modifications of the list in the
     * caller. The items in the list shall not be modified concurrently, though.
     *
     * @param p the project for which we find the repository info list
     * @return Copy of a list of repository info or empty list if no info is
     * found
     */
    public List<RepositoryInfo> getRepositoryInfo(Project p) {
        if (!cfg.isAllowed(p)) {
            return new ArrayList<>();
        }
        Map<Project, List<RepositoryInfo>> map = cfg.getEnv().getProjectRepositoriesMap();
        List<RepositoryInfo> info = map.get(p);
        return info == null ? new ArrayList<>() : new ArrayList<>(info);
    }

    /**
     * Get repository info list for particular project. A copy of the list is
     * returned always to allow concurrent modifications of the list in the
     * caller. The items in the list shall not be modified concurrently, though.
     * This list is sorted with respect {@link #REPOSITORY_NAME_COMPARATOR}.
     *
     * @param p the project for which we find the repository info list
     * @return Copy of a list of repository info or empty list if no info is
     * found
     */
    public List<RepositoryInfo> getSortedRepositoryInfo(Project p) {
        return getRepositoryInfo(p)
                .stream()
                .sorted(REPOSITORY_NAME_COMPARATOR)
                .collect(Collectors.toList());
    }

    /**
     * Generates ungrouped projects and repositories.
     */
    private void populateGroups() {
        groups.addAll(cfg.getEnv().getGroups().values());
        for (Project project : cfg.getEnv().getProjectList()) {
            // filterProjects() only adds groups which match project's name.
            Set<Group> copy = Group.matching(project, groups);

            // If no group matches the project, add it to not-grouped projects.
            if (copy.isEmpty()) {
                if (cfg.getEnv().getProjectRepositoriesMap().get(project) == null) {
                    ungroupedProjects.add(project);
                } else {
                    ungroupedRepositories.add(project);
                }
            }
        }

        // populate all grouped
        for (Group g : getGroups()) {
            allProjects.addAll(g.getProjects());
            allRepositories.addAll(g.getRepositories());
        }
    }

    /**
     * Filters set of projects based on the authorizer options
     * and whether the project is indexed.
     *
     * @param p set of projects
     * @return filtered set of projects
     */
    private Set<Project> filterProjects(Set<Project> p) {
        Set<Project> repos = new TreeSet<>(p);
        repos.removeIf(t -> !cfg.isAllowed(t) || !t.isIndexed());
        return repos;
    }

    /**
     * Filters set of groups based on the authorizer options.
     *
     * @param p set of groups
     * @return filtered set of groups
     */
    private Set<Group> filterGroups(Set<Group> p) {
        Set<Group> grps = new TreeSet<>(p);
        grps.removeIf(t -> !(cfg.isAllowed(t) || hasAllowedSubgroup(t)));
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
        return cacheProjects(PROJECT_HELPER_UNGROUPED_PROJECTS, ungroupedProjects);
    }

    /**
     * @return filtered ungrouped repositories
     */
    public Set<Project> getRepositories() {
        return cacheProjects(PROJECT_HELPER_UNGROUPED_REPOSITORIES, ungroupedRepositories);
    }

    /**
     * @param g group
     * @return filtered group's projects
     */
    public Set<Project> getProjects(Group g) {
        if (!cfg.isAllowed(g)) {
            return new TreeSet<>();
        }
        return cacheProjects(PROJECT_HELPER_GROUPED_PROJECT_GROUP +
                g.getName().toLowerCase(Locale.ROOT), g.getProjects());
    }

    /**
     * @param g group
     * @return filtered group's repositories
     */
    public Set<Project> getRepositories(Group g) {
        if (!cfg.isAllowed(g)) {
            return new TreeSet<>();
        }
        return cacheProjects(PROJECT_HELPER_GROUPED_REPOSITORIES_GROUP +
                g.getName().toLowerCase(Locale.ROOT), g.getRepositories());
    }

    /**
     * @return filtered grouped projects
     */
    public Set<Project> getGroupedProjects() {
        return cacheProjects(PROJECT_HELPER_GROUPED_PROJECTS, allProjects);
    }

    /**
     * @return filtered grouped repositories
     */
    public Set<Project> getGroupedRepositories() {
        return cacheProjects(PROJECT_HELPER_GROUPED_REPOSITORIES, allRepositories);
    }

    /**
     * @see #getProjects()
     * @return filtered ungrouped projects
     */
    public Set<Project> getUngroupedProjects() {
        return cacheProjects(PROJECT_HELPER_UNGROUPED_PROJECTS, ungroupedProjects);
    }

    /**
     * @see #getRepositories()
     * @return filtered ungrouped projects
     */
    public Set<Project> getUngroupedRepositories() {
        return cacheProjects(PROJECT_HELPER_UNGROUPED_REPOSITORIES, ungroupedRepositories);
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
        return cacheGroups(PROJECT_HELPER_SUBGROUPS_OF +
                g.getName().toLowerCase(Locale.ROOT), g.getSubgroups());
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
            p = new TreeMap<>();
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
            p = new TreeMap<>();
            cfg.setRequestAttribute(PROJECT_HELPER_FAVOURITE_GROUP, p);
        }
        val = p.get(group.getName());
        if (val == null) {
            Set<Project> favourite = getAllGrouped();
            favourite.removeIf(t -> {
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
            });
            val = !favourite.isEmpty();
            p.put(group.getName(), val);
        }
        cfg.setRequestAttribute(PROJECT_HELPER_FAVOURITE_GROUP, p);
        return val;
    }

    /**
     * Checks if the project is a favourite project.
     *
     * @param project project
     * @return true if it is favourite
     */
    public boolean isFavourite(Project project) {
        return cfg.getCookieVals(OPEN_GROK_PROJECT).contains(project.getName());
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
        Set<Project> set = new TreeSet<>();
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
