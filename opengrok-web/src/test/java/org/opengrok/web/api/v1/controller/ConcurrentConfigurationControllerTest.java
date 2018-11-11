package org.opengrok.web.api.v1.controller;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.suggester.provider.service.SuggesterService;

public class ConcurrentConfigurationControllerTest extends JerseyTest {

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @Mock
    private SuggesterService suggesterService;

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

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

    @Test
    @ConditionalRun(RepositoryInstalled.GitInstalled.class)
    public void testConcurrentConfigurationReloads() throws InterruptedException, IOException {
        final String origSourceRootPath = env.getSourceRootPath();
        final String origDataRootPath = env.getDataRootPath();
        final Map<String, Project> origProjects = env.getProjects();
        final List<RepositoryInfo> origRepositories = env.getRepositories();

        final int nThreads = Math.max(40, Runtime.getRuntime().availableProcessors() * 2);
        final int nProjects = 20;

        // prepare test repository
        TestRepository repository = new TestRepository();
        repository.create(HistoryGuru.class.getResourceAsStream("repositories.zip"));

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());

        final CountDownLatch latch = new CountDownLatch(nThreads);

        List<RepositoryInfo> repositoryInfos = new ArrayList<>();
        Map<String, Project> projects = new TreeMap<>();

        /*
         * Prepare nProjects git repositories, named project-{i} in the test repositories directory.
         */
        for (int i = 0; i < nProjects; i++) {
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

        /*
         * Now run setting a value in parallel, which triggers configuration reload.
         */
        for (int i = 0; i < nThreads; i++) {
            new Thread(() -> {
                Response put = target("configuration")
                        .path("projectsEnabled")
                        .request()
                        .put(Entity.text("true"));
                Assert.assertEquals(204, put.getStatus());
                latch.countDown();
            }).start();
        }

        latch.await();

        Assert.assertEquals(nProjects, env.getProjects().size());
        Assert.assertEquals(nProjects, env.getRepositories().size());
        Assert.assertEquals(nProjects, env.getProjectRepositoriesMap().size());
        env.getProjectRepositoriesMap().forEach((project, repositories) -> {
            Assert.assertEquals(1, repositories.size());
        });

        repository.destroy();

        env.setProjects(origProjects);
        env.setRepositories(origRepositories);
        env.setSourceRoot(origSourceRootPath);
        env.setDataRoot(origDataRootPath);
    }
}
