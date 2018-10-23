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
package org.opengrok.web.api.v1.controller;

import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryException;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.Repository;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.index.IndexDatabase;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.ClassUtil;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.web.api.v1.suggester.provider.service.SuggesterService;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.opengrok.indexer.history.RepositoryFactory.getRepository;

@Path("/projects")
public class ProjectsController {

    private static final Logger logger = LoggerFactory.getLogger(ProjectsController.class);

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Inject
    private SuggesterService suggester;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public Response addProject(final String projectName) {
        File srcRoot = env.getSourceRootFile();
        File projDir = new File(srcRoot, projectName);

        if (!env.getProjects().containsKey(projectName)) {
            Project project = new Project(projectName, "/" + projectName);

            // Add repositories in this project.
            List<RepositoryInfo> repos = getRepositoriesInDir(projDir);

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
            List<RepositoryInfo> repos = getRepositoriesInDir(projDir);
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

        return Response.status(Response.Status.CREATED).build();
    }

    private List<RepositoryInfo> getRepositoriesInDir(final File projDir) {

        HistoryGuru histGuru = HistoryGuru.getInstance();

        // There is no need to perform the work of invalidateRepositories(),
        // since addRepositories() calls getRepository() for each of
        // the repos.
        return new ArrayList<>(histGuru.addRepositories(new File[]{projDir}, env.getIgnoredNames()));
    }

    @DELETE
    @Path("/{project}")
    public void deleteProject(@PathParam("project") final String projectName)
            throws IOException, HistoryException {

        Project proj = env.getProjects().get(projectName);
        if (proj == null) {
            throw new IllegalStateException("cannot get project \"" + projectName + "\"");
        }

        logger.log(Level.INFO, "deleting configuration for project {0}", projectName);

        // Remove the project from its groups.
        for (Group group : proj.getGroups()) {
            group.getRepositories().remove(proj);
            group.getProjects().remove(proj);
        }

        // Now remove the repositories associated with this project.
        List<RepositoryInfo> repos = env.getProjectRepositoriesMap().get(proj);
        if (repos != null) {
            env.getRepositories().removeAll(repos);
        }
        env.getProjectRepositoriesMap().remove(proj);

        env.getProjects().remove(projectName, proj);

        // Prevent the project to be included in new searches.
        env.refreshSearcherManagerMap();

        // Lastly, remove data associated with the project.
        logger.log(Level.INFO, "deleting data for project {0}", projectName);
        for (String dirName: new String[]{IndexDatabase.INDEX_DIR, IndexDatabase.XREF_DIR}) {
            java.nio.file.Path path = Paths.get(env.getDataRootPath(), dirName, projectName);
            try {
                IOUtils.removeRecursive(path);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not delete {0}", path.toString());
            }
        }
        HistoryGuru guru = HistoryGuru.getInstance();
        guru.removeCache(repos.stream().
                map(x -> {
                    try {
                        return env.getPathRelativeToSourceRoot(new File((x).getDirectoryName()));
                    } catch (ForbiddenSymlinkException e) {
                        logger.log(Level.FINER, e.getMessage());
                        return "";
                    } catch (IOException e) {
                        logger.log(Level.INFO, "cannot remove files for repository {0}", x.getDirectoryName());
                        // Empty output should not cause any harm
                        // since {@code getReposFromString()} inside
                        // {@code removeCache()} will return nothing.
                        return "";
                    }
                }).collect(Collectors.toSet()));

        suggester.delete(projectName);
    }

    @PUT
    @Path("/{project}/indexed")
    @Consumes(MediaType.TEXT_PLAIN)
    public void markIndexed(@PathParam("project") final String projectName) throws Exception {

        Project project = env.getProjects().get(projectName);
        if (project != null) {
            project.setIndexed(true);

            // Refresh current version of the project's repositories.
            List<RepositoryInfo> riList = env.getProjectRepositoriesMap().get(project);
            if (riList != null) {
                for (RepositoryInfo ri : riList) {
                    Repository repo = getRepository(ri, false);

                    if (repo != null && repo.getCurrentVersion() != null && repo.getCurrentVersion().length() > 0) {
                        // getRepository() always creates fresh instance
                        // of the Repository object so there is no need
                        // to call setCurrentVersion() on it.
                        ri.setCurrentVersion(repo.determineCurrentVersion());
                    }
                }
            }
            suggester.refresh(projectName);
        } else {
            logger.log(Level.WARNING, "cannot find project {0} to mark as indexed", projectName);
        }

        // In case this project has just been incrementally indexed,
        // its IndexSearcher needs a poke.
        env.maybeRefreshIndexSearchers(Collections.singleton(projectName));

        env.refreshDateForLastIndexRun();
    }

    @PUT
    @Path("/{project}/property/{field}")
    public void set(
            @PathParam("project") final String projectName,
            @PathParam("field") final String field,
            final String value
    ) throws Exception {
        Project project = env.getProjects().get(projectName);
        if (project != null) {
            // Set the property.
            ClassUtil.setFieldValue(project, field, value);

            // Refresh repositories for this project as well.
            List<RepositoryInfo> riList = env.getProjectRepositoriesMap().get(project);
            if (riList != null) {
                for (RepositoryInfo ri : riList) {
                    Repository repo = getRepository(ri, false);

                    // set the property
                    ClassUtil.setFieldValue(repo, field, value);
                }
            }
        } else {
            logger.log(Level.WARNING, "cannot find project {0} to set a property", projectName);
        }
    }

    @GET
    @Path("/{project}/property/{field}")
    @Produces(MediaType.APPLICATION_JSON)
    public Object get(@PathParam("project") final String projectName, @PathParam("field") final String field)
            throws IOException {

        Project project = env.getProjects().get(projectName);
        if (project == null) {
            throw new WebApplicationException(
                    "cannot find project " + projectName + " to get a property", Response.Status.BAD_REQUEST);
        }
        return ClassUtil.getFieldValue(project, field);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> listProjects() {
        return env.getProjectNames();
    }

    @GET
    @Path("indexed")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> listIndexed() {
        return env.getProjectList().stream()
                .filter(Project::isIndexed)
                .map(Project::getName)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{project}/repositories")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getRepositories(@PathParam("project") final String projectName) {
        Project project = env.getProjects().get(projectName);
        if (project != null) {
            List<RepositoryInfo> infos = env.getProjectRepositoriesMap().get(project);
            if (infos != null) {
                return infos.stream()
                        .map(RepositoryInfo::getDirectoryNameRelative)
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    @GET
    @Path("/{project}/repositories/type")
    @Produces(MediaType.APPLICATION_JSON)
    public Set<String> getRepositoriesType(@PathParam("project") final String projectName) {
        Project project = env.getProjects().get(projectName);
        if (project != null) {
            List<RepositoryInfo> infos = env.getProjectRepositoriesMap().get(project);
            if (infos != null) {
                return infos.stream()
                        .map(RepositoryInfo::getType)
                        .collect(Collectors.toSet());
            }
        }
        return Collections.emptySet();
    }

}
