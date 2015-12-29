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
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.RepositoryInfo;

/**
 * Preprocessing of projects, repositories and groups for the UI
 *
 * @author Krystof Tulinger
 * @version $Revision$
 */
public final class ProjectHelper {

    private final PageConfig cfg;
    /**
     * Map of [project name] -> [repositories] mapping
     */
    private final Map<Project, List<RepositoryInfo>> map;
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

    private static ProjectHelper instance;

    private ProjectHelper(PageConfig cfg) throws IOException {
        this.cfg = cfg;
        map = new TreeMap<>();
        groups = new TreeSet<>(cfg.getEnv().getGroups());
        projects = new TreeSet<>();
        repositories = new TreeSet<>();

        generateTreeMap();
        generate();

    }

    public static ProjectHelper getInstance(PageConfig cfg) throws IOException {
        if (instance == null) {
            return instance = new ProjectHelper(cfg);
        }
        return instance;
    }

    /**
     * Generate a TreeMap of projects with corresponding repository information
     * Project with some repository information are considered as repositories
     * otherwise they're just simple projects
     *
     * @throws IOException
     */
    private void generateTreeMap() throws IOException {
        RuntimeEnvironment env = cfg.getEnv();

        for (RepositoryInfo r : env.getRepositories()) {
            Project proj;
            String repoPath = env.getPathRelativeToSourceRoot(
                    new File(r.getDirectoryName()), 0);

            if ((proj = Project.getProject(repoPath)) != null) {
                List<RepositoryInfo> values = map.get(proj);
                if (values == null) {
                    values = new ArrayList<>();
                    map.put(proj, values);
                }
                values.add(r);
            }
        }
    }

    /**
     * Get repository info for particular project
     *
     * @param p Project
     * @return List of repository info or empty List if no info is found
     */
    public List<RepositoryInfo> getRepositoryInfo(Project p) {
        List<RepositoryInfo> info = map.get(p);
        return info == null ? new ArrayList<>() : info;
    }

    /**
     * Generates full group - project/repository tree
     */
    private void generate() {
        for (Project project : cfg.getEnv().getProjects()) {
            // filter only groups which match project's description
            Set<Group> copy = new TreeSet<Group>(groups);
            copy.removeIf(new Predicate<Group>() {
                @Override
                public boolean test(Group g) {
                    return !g.match(project);
                }
            });

            // add project to the groups
            for (Group group : copy) {
                if (map.get(project) == null) {
                    group.addProject(project);
                } else {
                    group.addRepository(project);
                }
            }

            // if no group matches the project, add it to not-grouped projects
            if (copy.isEmpty()) {
                if (map.get(project) == null) {
                    projects.add(project);
                } else {
                    repositories.add(project);
                }
            }
        }
    }

    public Set<Group> getGroups() {
        return groups;
    }

    public Set<Project> getProjects() {
        return projects;
    }

    public Set<Project> getRepositories() {
        return repositories;
    }

    public Set<Project> getGroupedProjects() {
        Set<Project> p = new TreeSet<Project>();
        for (Group g : groups) {
            p.addAll(g.getProjects());
        }
        return p;
    }

    public Set<Project> getGroupedRepositories() {
        Set<Project> p = new TreeSet<Project>();
        for (Group g : groups) {
            p.addAll(g.getRepositories());
        }
        return p;
    }

    public Set<Project> getUngroupedProjects() {
        return projects;
    }

    public Set<Project> getUngroupedRepositories() {
        return repositories;
    }

    /**
     * @return Set of all projects and repositories with group
     */
    public Set<Project> getAllGrouped() {
        return mergeProjects(getGroupedProjects(), getGroupedRepositories());
    }

    /**
     * @param g group
     * @return Set of all projects and repositories in group g
     */
    public Set<Project> getAllGrouped(Group g) {
        return mergeProjects(g.getProjects(), g.getRepositories());
    }

    /**
     * @return Set of all projects and repositories without group
     */
    public Set<Project> getAllUngrouped() {
        return mergeProjects(getUngroupedProjects(), getUngroupedRepositories());
    }

    /**
     * @return Set of all projects and repositories no matter if grouped or
     * ungrouped
     */
    public Set<Project> getAllProjects() {
        return mergeProjects(getAllUngrouped(), getAllGrouped());
    }

    public static Set<Project> mergeProjects(Set<Project> p1, Set<Project> p2) {
        Set<Project> set = new TreeSet<Project>();
        set.addAll(p1);
        set.addAll(p2);
        return set;
    }
}
