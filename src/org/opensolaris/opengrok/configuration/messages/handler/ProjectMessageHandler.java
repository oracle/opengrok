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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages.handler;

import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.messages.Message;
import org.opensolaris.opengrok.configuration.messages.MessageHandler;
import org.opensolaris.opengrok.configuration.messages.Response;
import org.opensolaris.opengrok.configuration.messages.ValidationException;
import org.opensolaris.opengrok.history.HistoryException;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.history.Repository;
import org.opensolaris.opengrok.history.RepositoryInfo;
import org.opensolaris.opengrok.index.IndexDatabase;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.ForbiddenSymlinkException;
import org.opensolaris.opengrok.util.IOUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.opensolaris.opengrok.history.RepositoryFactory.getRepository;

public class ProjectMessageHandler implements MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectMessageHandler.class);

    private RuntimeEnvironment env;

    public ProjectMessageHandler(final RuntimeEnvironment env) {
        if (env == null) {
            throw new IllegalArgumentException("Environment cannot be null");
        }
        this.env = env;
    }

    @Override
    public Response handle(final Message message) throws HandleException {
        try {
            validateMore(env, message);
        } catch (ValidationException e) {
            throw new HandleException(e);
        }

        String command = message.getText();
        switch (command) {
            case "add":
                addProjects(message.getTags());
                break;
            case "delete":
                deleteProjects(message.getTags());
                break;
            case "indexed":
                markAsIndexed(message.getTags());
                break;
            case "list":
                return Response.of(env.getProjectNames().stream().collect(Collectors.joining("\n")));
            case "list-indexed":
                return Response.of(env.getProjectList().stream().filter(Project::isIndexed).
                        map(Project::getName).collect(Collectors.joining("\n")));
            case "get-repos":
                return Response.of(mapRepositories(message.getTags(), RepositoryInfo::getDirectoryNameRelative)
                        .collect(Collectors.joining("\n")));
            case "get-repos-type":
                return Response.of(mapRepositories(message.getTags(), RepositoryInfo::getType).distinct().sorted()
                        .collect(Collectors.joining("\n")));
            default:
                throw new HandleException("Unknown command " + command);
        }

        return Response.of("command \"" + message.getText() + "\" for projects " +
                String.join(",", message.getTags()) + " done");
    }

    /**
     * Perform additional validation. This cannot be done in validate()
     * because it does not have access to the currently used RuntimeEnvironment.
     */
    private void validateMore(RuntimeEnvironment env, Message message) throws ValidationException {
        String command = message.getText();

        if (!env.isProjectsEnabled()) {
            throw new ValidationException("Projects have to be enabled in order to use this message");
        }

        // The same check can be done for the "delete" command however it is better
        // to allow for some flexibility. The source can be deleted first and then
        // the "delete" command can follow.
        if (command.equals("add")) {
            File srcRoot = env.getSourceRootFile();
            for (String projectName : message.getTags()) {
                File projDir = new File(srcRoot, projectName);
                if (!projDir.isDirectory()) {
                    throw new ValidationException(projDir.getAbsolutePath() + " is not a directory");
                }
            }
        }

        if (command.equals("delete")) {
            for (String projectName : message.getTags()) {
                if (!env.getProjects().containsKey(projectName)) {
                    throw new ValidationException("project \"" + projectName +
                            "\" not found in configuration");
                }
            }
        }
    }

    private void addProjects(final Set<String> projectNames) {
        for (String projectName : projectNames) {
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
    }

    private void deleteProjects(final Set<String> projectNames) throws HandleException {
        for (String projectName : projectNames) {
            Project proj = env.getProjects().get(projectName);
            if (proj == null) {
                throw new HandleException("cannot get project \"" + projectName + "\"");
            }

            LOGGER.log(Level.INFO, "deleting configuration for project {0}", projectName);

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
            LOGGER.log(Level.INFO, "deleting data for project {0}", projectName);
            for (String dirName: new String[]{
                    IndexDatabase.INDEX_DIR, IndexDatabase.XREF_DIR}) {

                try {
                    IOUtils.removeRecursive(Paths.get(env.getDataRootPath() +
                            File.separator + dirName +
                            File.separator + projectName));
                } catch (IOException e) {
                    throw new HandleException(e);
                }
            }
            HistoryGuru guru = HistoryGuru.getInstance();
            try {
                guru.removeCache(repos.stream().
                        map((x) -> {
                            try {
                                return env.getPathRelativeToSourceRoot(
                                        new File((x).getDirectoryName())
                                );
                            } catch (ForbiddenSymlinkException e) {
                                LOGGER.log(Level.FINER, e.getMessage());
                                return "";
                            } catch (IOException e) {
                                LOGGER.log(Level.INFO,
                                        "cannot remove files for repository {0}",
                                        x.getDirectoryName());
                                // Empty output should not cause any harm
                                // since {@code getReposFromString()} inside
                                // {@code removeCache()} will return nothing.
                                return "";
                            }
                        }).collect(Collectors.toSet()));
            } catch (HistoryException e) {
                throw new HandleException(e);
            }
        }
    }

    private void markAsIndexed(final Set<String> projectNames) throws HandleException {
        for (String projectName : projectNames) {
            Project project;
            if ((project = env.getProjects().get(projectName)) != null) {
                project.setIndexed(true);

                // Refresh current version of the project's repositories.
                List<RepositoryInfo> riList = env.getProjectRepositoriesMap().get(project);
                if (riList != null) {
                    for (RepositoryInfo ri : riList) {
                        Repository repo = null;
                        try {
                            repo = getRepository(ri);
                        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException
                                | InvocationTargetException e) {
                            throw new HandleException(e);
                        }

                        if (repo != null && repo.getCurrentVersion() != null &&
                                repo.getCurrentVersion().length() > 0) {
                            // getRepository() always creates fresh instance
                            // of the Repository object so there is no need
                            // to call setCurrentVersion() on it.
                            try {
                                ri.setCurrentVersion(repo.determineCurrentVersion());
                            } catch (IOException e) {
                                throw new HandleException(e);
                            }
                        }
                    }
                }
            } else {
                LOGGER.log(Level.WARNING, "cannot find project {0} to mark as indexed", projectName);
            }
        }

        // In case this project has just been incrementally indexed,
        // its IndexSearcher needs a poke.
        env.maybeRefreshIndexSearchers(projectNames);

        env.refreshDateForLastIndexRun();
    }

    private List<RepositoryInfo> getRepositoriesInDir(RuntimeEnvironment env, File projDir) {

        HistoryGuru histGuru = HistoryGuru.getInstance();

        // There is no need to perform the work of invalidateRepositories(),
        // since addRepositories() calls getRepository() for each of
        // the repos.
        return new ArrayList<>(histGuru.addRepositories(new File[]{projDir},
                env.getIgnoredNames()));
    }

    private <T> Stream<T> mapRepositories(final Set<String> projectNames, final Function<RepositoryInfo, T> mapper) {
        Stream.Builder<T> builder = Stream.builder();
        for (String projectName : projectNames) {
            Project project = env.getProjects().get(projectName);
            if (project == null) {
                continue;
            }
            List<RepositoryInfo> infos = env.getProjectRepositoriesMap().get(project);
            if (infos != null) {
                infos.stream().map(mapper).forEach(builder::add);
            }
        }

        return builder.build();
    }

}
