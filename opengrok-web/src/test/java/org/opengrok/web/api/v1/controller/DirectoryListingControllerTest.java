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
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.ServletDeploymentContext;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.NullableNumLinesLOC;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.search.DirectoryEntry;
import org.opengrok.indexer.util.FileExtraZipper;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.web.DummyHttpServletRequest;
import org.opengrok.indexer.web.SearchHelper;
import org.opengrok.indexer.web.SortOrder;
import org.opengrok.web.DirectoryListing;
import org.opengrok.web.PageConfig;
import org.opengrok.web.api.v1.RestApp;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.web.api.v1.controller.DirectoryListingController.getDirectoryEntriesDTO;

class DirectoryListingControllerTest extends OGKJerseyTest {
    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repository;

    @Override
    protected DeploymentContext configureDeployment() {
        return ServletDeploymentContext.forServlet(new ServletContainer(new RestApp())).build();
    }

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new GrizzlyWebTestContainerFactory();
    }

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResource("/repositories"));

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setProjectsEnabled(true);
        env.setHistoryEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);

        Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                // don't create dictionary
                null, // subFiles - needed when refreshing history partially
                null); // repositories - needed when refreshing history partially

        // Run the indexer so that LOC fields are populated.
        Project project = Project.getProject("/git");
        assertNotNull(project);
        Indexer.getInstance().doIndexerExecution(Set.of(project), null);
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        // This should match Configuration constructor.
        env.setProjects(new ConcurrentHashMap<>());
        env.setRepositories(new ArrayList<>());
        env.getProjectRepositoriesMap().clear();

        repository.destroy();
    }

    /**
     * Basic negative test that two {@link DirectoryListingController.DirectoryEntryDTO}
     * instances are not equal if their paths are not equal.
     * Also makes a nice test for
     * {@link DirectoryListingController.DirectoryEntryDTO#DirectoryEntryDTO(DirectoryEntry)}
     * in the case when the 'extra' field carrying LOC is {@code null}.
     * Ideally, all the fields should be tested like this.
     */
    @Test
    void testDirectoryEntryDTONotEquals() throws Exception {
        File file1 = Path.of(repository.getSourceRoot(), "git", "main.c").toFile();
        DirectoryEntry entry1 = new DirectoryEntry(file1);
        assertNull(entry1.getExtra());
        DirectoryListingController.DirectoryEntryDTO entryDTO1 = new DirectoryListingController.DirectoryEntryDTO(entry1);

        File file2 = Path.of(repository.getSourceRoot(), "git", "header.h").toFile();
        DirectoryEntry entry2 = new DirectoryEntry(file2);
        assertNull(entry2.getExtra());
        DirectoryListingController.DirectoryEntryDTO entryDTO2 = new DirectoryListingController.DirectoryEntryDTO(entry2);

        assertNotEquals(entryDTO1, entryDTO2);
    }

    /**
     * Basic positive test of {@link DirectoryListingController.DirectoryEntryDTO#DirectoryEntryDTO(DirectoryEntry)}.
     */
    @Test
    void testDirectoryEntryDTOConstruction() throws Exception {
        File file = Path.of(repository.getSourceRoot(), "git", "main.c").toFile();
        DirectoryEntry entry = new DirectoryEntry(file);
        final Date date = new Date(123000000);
        entry.setDate(date);
        final String description = "foo";
        entry.setDescription(description);
        final String pathDescription = "bar";
        entry.setPathDescription(pathDescription);
        NullableNumLinesLOC extra = new NullableNumLinesLOC("/git/main.c", 42L, 24L);
        entry.setExtra(extra);
        DirectoryListingController.DirectoryEntryDTO entryDTO = new DirectoryListingController.DirectoryEntryDTO(entry);

        DirectoryListingController.DirectoryEntryDTO entryDTOexp = new DirectoryListingController.DirectoryEntryDTO();
        entryDTOexp.path = "/git/main.c";
        entryDTOexp.date = date;
        entryDTOexp.description = description;
        entryDTOexp.pathDescription = pathDescription;
        entryDTOexp.numLines = 42L;
        entryDTOexp.loc = 24L;

        assertEquals(entryDTOexp, entryDTO);
    }

    @Test
    void testDirectoryListing() throws Exception {
        final String path = "git";
        int size = 6;
        Response response = target("list")
                .queryParam("path", "/" + path)
                .request()
                .get();
        List<DirectoryListingController.DirectoryEntryDTO> entriesResp = response.readEntity(new GenericType<>() {
        });
        assertNotNull(entriesResp);
        assertEquals(size, entriesResp.size());

        File file = new File(repository.getSourceRoot(), path);
        assertTrue(file.isDirectory());
        DirectoryListing dl = new DirectoryListing();
        File[] files = file.listFiles();
        List<DirectoryEntry> entries = dl.createDirectoryEntries(file, path, PageConfig.getSortedFiles(files));

        Project project = Project.getProject("/" + path);
        assertNotNull(project);
        DummyHttpServletRequest request = new DummyHttpServletRequest();
        request.setContextPath("foo");
        request.setServletPath("bar");
        PageConfig pageConfig = PageConfig.get(path, request);
        SearchHelper searchHelper = pageConfig.prepareInternalSearch(SortOrder.RELEVANCY);
        searchHelper.prepareExec(project);
        List<NullableNumLinesLOC> extras = pageConfig.getNullableNumLinesLOCS(project, searchHelper);
        FileExtraZipper zipper = new FileExtraZipper();
        zipper.zip(entries, extras);

        List<DirectoryListingController.DirectoryEntryDTO> entriesDTO = getDirectoryEntriesDTO(entries);
        assertEquals(entriesDTO.size(), entriesResp.size());
        assertEquals(entriesDTO, entriesResp);
    }
}
