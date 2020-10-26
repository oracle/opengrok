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
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.suggester.provider.service.SuggesterService;

@ConditionalRun(RepositoryInstalled.GitInstalled.class)
public class ConcurrentConfigurationControllerTest extends OGKJerseyTest {

    private static final int PROJECTS_COUNT = 20;
    private static final int THREAD_COUNT = Math.max(30, Runtime.getRuntime().availableProcessors() * 2);
    private static final int TASK_COUNT = 100;

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @Mock
    private SuggesterService suggesterService;

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    TestRepository repository;
    String origSourceRootPath;
    String origDataRootPath;
    Map<String, Project> origProjects;
    List<RepositoryInfo> origRepositories;

    @Override
    protected Application configure() {
        MockitoAnnotations.initMocks(this);
        return new ResourceConfig(ConfigurationController.class)
                .register(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(suggesterService).to(SuggesterService.class);
                    }
                });
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        origSourceRootPath = env.getSourceRootPath();
        origDataRootPath = env.getDataRootPath();
        origProjects = env.getProjects();
        origRepositories = env.getRepositories();

        // prepare test repository
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResourceAsStream("repositories.zip"));

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());


        List<RepositoryInfo> repositoryInfos = new ArrayList<>();
        Map<String, Project> projects = new TreeMap<>();

        /*
         * Prepare PROJECTS_COUNT git repositories, named project-{i} in the test repositories directory.
         */
        for (int i = 0; i < PROJECTS_COUNT; i++) {
            Project project = new Project();
            project.setName("project-" + i);
            project.setPath("/project-" + i);
            RepositoryInfo repo = new RepositoryInfo();
            repo.setDirectoryNameRelative("/project-" + i);

            projects.put("project-" + i, project);
            repositoryInfos.add(repo);

            // create the repository
            FileUtils.copyDirectory(
                    Paths.get(repository.getSourceRoot(), "git").toFile(),
                    Paths.get(repository.getSourceRoot(), "project-" + i).toFile()
            );
        }

        env.setRepositories(repositoryInfos);
        env.setProjects(projects);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        repository.destroy();

        env.setProjects(origProjects);
        env.setRepositories(origRepositories);
        env.setSourceRoot(origSourceRootPath);
        env.setDataRoot(origDataRootPath);
    }

    @Test
    public void testConcurrentConfigurationReloads() throws InterruptedException, ExecutionException {
        final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<?>> futures = new LinkedList<>();

        /*
         * Now run setting a value in parallel which triggers configuration reload.
         */
        for (int i = 0; i < TASK_COUNT; i++) {
            futures.add(threadPool.submit(() -> {
                Response put = target("configuration")
                        .path("projectsEnabled")
                        .request()
                        .put(Entity.text("true"));

                Assert.assertEquals(204, put.getStatus());

                assertTestedProjects();
            }));
        }

        threadPool.shutdown();
        threadPool.awaitTermination(1, TimeUnit.MINUTES);

        for (Future<?> future : futures) {
            // calling get on a future will rethrow the exceptions in its thread (i. e. assertions in this case)
            future.get();
        }
    }

    @Test
    public void testConcurrentCInvalidateRepositories() throws InterruptedException, ExecutionException {
        final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_COUNT);
        final List<RepositoryInfo> repositoryInfos = env.getRepositories();
        List<Future<?>> futures = new LinkedList<>();

        /*
         * Now run apply config in parallel
         */
        for (int i = 0; i < TASK_COUNT; i++) {
            futures.add(threadPool.submit(() -> {
                env.applyConfig(false, CommandTimeoutType.RESTFUL);
                assertTestedProjects();
            }));
        }

        threadPool.shutdown();
        threadPool.awaitTermination(1, TimeUnit.MINUTES);

        for (Future<?> future : futures) {
            // calling get on a future will rethrow the exceptions in its thread (i. e. assertions in this case)
            future.get();
        }
    }

    private void assertTestedProjects() {
        Assert.assertEquals(PROJECTS_COUNT, env.getProjects().size());
        Assert.assertEquals(PROJECTS_COUNT, env.getRepositories().size());
        Assert.assertEquals(PROJECTS_COUNT, env.getProjectRepositoriesMap().size());
        env.getProjectRepositoriesMap().forEach((project, repositories) -> {
            Assert.assertEquals(1, repositories.size());
        });
    }
}
