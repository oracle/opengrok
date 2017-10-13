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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.history.Repository;
import static org.opensolaris.opengrok.history.RepositoryFactory.getRepository;
import org.opensolaris.opengrok.history.RepositoryInfo;
import org.opensolaris.opengrok.index.IndexDatabase;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.IOUtils;


/**
 * @author Vladimir Kotal
 */
public class ProjectMessage extends Message {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectMessage.class);

    /**
     * Perform additional validation. This cannot be done in validate()
     * because it does not have access to the currently used RuntimeEnvironment.
     */
    private void validateMore(RuntimeEnvironment env) throws Exception {
        String command = getText();

        if (!env.isProjectsEnabled()) {
            throw new Exception("Projects have to be enabled in order to use this message");
        }

        // The same check can be done for the "delete" command however it is better
        // to allow for some flexibility. The source can be deleted first and then
        // the "delete" command can follow.
        if (command.compareTo("add") == 0) {
            File srcRoot = env.getSourceRootFile();
            for (String projectName : getTags()) {
                File projDir = new File(srcRoot, projectName);
                if (!projDir.isDirectory()) {
                    throw new Exception(projDir.getAbsolutePath() + " is not a directory");
                }
            }
        }

        if (command.compareTo("delete") == 0) {
            for (String projectName : getTags()) {
                if (!env.getProjects().containsKey(projectName)) {
                    throw new Exception("project \"" + projectName +
                        "\" not found in configuration");
                }
            }
        }
    }

    private List<RepositoryInfo> getRepositoriesInDir(RuntimeEnvironment env,
            File projDir) {

        HistoryGuru histGuru = HistoryGuru.getInstance();

        // There is no need to perform the work of invalidateRepositories(),
        // since addRepositories() calls getRepository() for each of
        // the repos.
        return new ArrayList<>(histGuru.addRepositories(new File[]{projDir},
            env.getIgnoredNames()));
    }

    @Override
    protected byte[] applyMessage(RuntimeEnvironment env) throws Exception {
        String command = getText();

        validateMore(env);

        switch (getText()) {
            case "add":
                for (String projectName : getTags()) {
                    File srcRoot = env.getSourceRootFile();
                    File projDir = new File(srcRoot, projectName);

                    if (!env.getProjects().containsKey(projectName)) {
                        Project project = new Project(projectName, "/" + projectName);
                        project.setTabSize(env.getConfiguration().getTabSize());

                        // Add repositories in this project.
                        List<RepositoryInfo> repos = getRepositoriesInDir(env, projDir);

                        env.addRepositories(repos);
                        env.getProjectRepositoriesMap().put(project, repos);

                        // Finally introduce the project to the configuration.
                        // Note that the project is inactive in the UI until it is indexed.
                        // See {@code isIndexed()}
                        env.getProjects().put(projectName, project);

                        Set<Project> projectSet = new TreeSet<>(); 
                        projectSet.add(project);
                        env.populateGroups(env.getGroups(), projectSet);
                    } else {
                        Project project = env.getProjects().get(projectName);
                        Map<Project, List<RepositoryInfo>> map = env.getProjectRepositoriesMap();

                        // Refresh the list of repositories of this project.
                        // This is the goal of this action: if an existing project
                        // is re-added, this means its list of repositories has changed.
                        List<RepositoryInfo> repos = getRepositoriesInDir(env, projDir);
                        List<RepositoryInfo> allrepos = env.getRepositories();
                        synchronized (allrepos) {
                            // newly added repository
                            for (RepositoryInfo repo : repos) {
                                if (!allrepos.contains(repo)) {
                                    allrepos.add(repo);
                                }
                            }
                            // deleted repository
                            for (RepositoryInfo repo : map.get(project)) {
                                if (!repos.contains(repo)) {
                                    allrepos.remove(repo);
                                }
                            }
                        }

                        map.put(project, repos);
                    }
                }
                break;
            case "delete":
                for (String projectName : getTags()) {
                    Project proj = env.getProjects().get(projectName);
                    if (proj == null) {
                        throw new Exception("cannot get project \"" + projectName + "\"");
                    }

                    LOGGER.log(Level.INFO, "deleting configuration for project " + projectName);

                    // Remove the project from its groups.
                    for (Group group : proj.getGroups()) {
                        group.getRepositories().remove(proj);
                        group.getProjects().remove(proj);
                    }

                    // Now remove the repositories associated with this project.
                    List<RepositoryInfo> repos = env.getProjectRepositoriesMap().get(proj);
                    env.getRepositories().removeAll(repos);
                    env.getProjectRepositoriesMap().remove(proj);

                    env.getProjects().remove(projectName, proj);

                    // Prevent the project to be included in new searches.
                    env.refreshSearcherManagerMap();

                    // Lastly, remove data associated with the project.
                    LOGGER.log(Level.INFO, "deleting data for project " + projectName);
                    for (String dirName: new String[]{
                        IndexDatabase.INDEX_DIR, IndexDatabase.XREF_DIR}) {

                            IOUtils.removeRecursive(Paths.get(env.getDataRootPath() +
                                File.separator + dirName +
                                File.separator + projectName));
                    }
                    HistoryGuru guru = HistoryGuru.getInstance();
                    guru.removeCache(repos.stream().
                        map((x) -> {
                            try {
                                return env.getPathRelativeToSourceRoot(
                                        new File((x).getDirectoryName())
                                );
                            } catch (IOException e) {
                                LOGGER.log(Level.INFO,
                                    "cannot remove files for repository " +
                                    x.getDirectoryName());
                                // Empty output should not cause any harm
                                // since {@code getReposFromString()} inside
                                // {@code removeCache()} will return nothing.
                                return "";
                            }
                        }).collect(Collectors.toSet()));
                }
                break;
            case "indexed":
                for (String projectName : getTags()) {
                    Project project;
                    if ((project = env.getProjects().get(projectName)) != null) {
                        project.setIndexed(true);

                        // Refresh current version of the project's repositories.
                        List<RepositoryInfo> riList = env.getProjectRepositoriesMap().get(project);
                        if (riList != null) {
                            for (RepositoryInfo ri : riList) {
                                Repository repo = getRepository(ri);

                                if (repo != null && repo.getCurrentVersion() != null &&
                                    repo.getCurrentVersion().length() > 0) {
                                        // getRepository() always creates fresh instance
                                        // of the Repository object so there is no need
                                        // to call setCurrentVersion() on it.
                                        ri.setCurrentVersion(repo.determineCurrentVersion());
                                }
                            }
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "cannot find project " +
                               projectName + " to mark as indexed");
                    }
                }

                // In case this project has just been incrementally indexed,
                // its IndexSearcher needs a poke.
                env.maybeRefreshIndexSearchers(getTags());

                env.refreshDateForLastIndexRun();
                break;
            case "list":
                return (env.getProjectNames().stream().collect(Collectors.joining("\n")).getBytes());
            case "list-indexed":
                return (env.getProjectList().stream().filter(p -> p.isIndexed()).
                        map(p -> p.getName()).collect(Collectors.joining("\n")).getBytes());
            case "get-repos":
                List<String> repos = new ArrayList<>();

                for (String projectName : getTags()) {
                    Project project;
                    if ((project = env.getProjects().get(projectName)) == null) {
                        continue;
                    }
                    List<RepositoryInfo> infos = env.getProjectRepositoriesMap().
                            get(project);
                    if (infos != null) {
                        repos.addAll(infos.stream().
                                map(ri -> ri.getDirectoryNameRelative()).
                                collect(Collectors.toList()));
                    }
                }

                return repos.stream().collect(Collectors.joining("\n")).getBytes();
            case "get-repos-type":
                Set<String> types = new TreeSet<>();

                for (String projectName : getTags()) {
                    Project project;
                    if ((project = env.getProjects().get(projectName)) == null) {
                        continue;
                    }
                    List<RepositoryInfo> infos = env.getProjectRepositoriesMap().
                            get(project);
                    if (infos != null) {
                        types.addAll(infos.stream().
                                map(ri -> ri.getType()).
                                collect(Collectors.toList()));
                    }
                }

                return types.stream().collect(Collectors.joining("\n")).getBytes();
        }

        return ("command \"" + getText() + "\" for projects " +
            String.join(",", getTags()) + " done").getBytes();
    }

    /**
     * Validate ProjectMessage.
     * Tags are project names, text is command (add/delete)
     * @throws Exception 
     */
    @Override
    public void validate() throws Exception {
        String command = getText();
        Set<String> allowedText = new TreeSet<>(Arrays.asList("add", "delete",
                "list", "list-indexed", "indexed", "get-repos",
                "get-repos-type"));

        // Text field carries the command.
        if (command == null) {
            throw new Exception("The message must contain a text - \"add\", \"delete\" or \"indexed\"");
        }
        if (!allowedText.contains(command)) {
            throw new Exception("The message must contain either 'add', " +
                    "'delete', 'indexed', 'list', 'list-indexed' or 'get-repos' text");
        }

        if (!command.startsWith("list") && getTags().isEmpty()) {
            throw new Exception("The message must contain a tag (project name(s))");        
        }

        super.validate();
    }
}
