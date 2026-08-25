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
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.filter.CorsFilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.web.api.v1.filter.CorsFilter.ALLOW_CORS_HEADER;
import static org.opengrok.web.api.v1.filter.CorsFilter.CORS_REQUEST_HEADER;
import static org.opengrok.web.api.v1.filter.CorsFilter.VARY_HEADER;

class AnnotationControllerTest extends OGKJerseyTest {

    private static final String HASH_BB74B7E8 = "bb74b7e849170c31dc1b1b5801c83bf0094a3b10";
    private static final String HASH_AA35C258 = "aa35c25882b9a60a97758e0ceb276a3f8cb4ae3a";

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repository;

    @Override
    protected Application configure() {
        return new ResourceConfig(AnnotationController.class, CorsFilter.class);
    }

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true"); // necessary to test CORS from controllers
        super.setUp();
        repository = new TestRepository();
        final URL repositoryURL = HistoryGuru.class.getResource("/repositories");
        assertNotNull(repositoryURL);
        repository.create(repositoryURL);

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setProjectsEnabled(true);
        env.setHistoryEnabled(true);
        env.setAllowedOrigins(Set.of("http://example.com"));
        RepositoryFactory.initializeIgnoredNames(env);

        Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                // don't create dictionary
                null, // subFiles - needed when refreshing history partially
                null); // repositories - needed when refreshing history partially
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        // This should match Configuration constructor.
        env.setProjects(new ConcurrentHashMap<>());
        env.setRepositories(new ArrayList<>());
        env.getProjectRepositoriesMap().clear();
        env.setAllowedOrigins(Collections.emptySet());

        repository.destroy();
    }

    private static int getNumLines(File file) throws IOException {
        int lines = 0;

        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            while (in.readLine() != null) {
                lines++;
            }
        }

        return lines;
    }

    @Test
    void testAnnotationAPI() throws IOException {
        final String path = "git/Makefile";
        List<AnnotationController.AnnotationDTO> annotations = target("annotation")
                .queryParam("path", path)
                .request()
                .get(new GenericType<>() {
                });
        assertEquals(getNumLines(new File(env.getSourceRootFile(), path)), annotations.size());
        assertEquals("Trond Norbye", annotations.getFirst().author());
        List<String> ids = annotations.stream().
                map(AnnotationController.AnnotationDTO::revision).
                collect(Collectors.toList());
        assertEquals(Arrays.asList(HASH_BB74B7E8, HASH_BB74B7E8, HASH_BB74B7E8, HASH_BB74B7E8, HASH_BB74B7E8,
                HASH_BB74B7E8, HASH_BB74B7E8, HASH_BB74B7E8, HASH_AA35C258, HASH_AA35C258, HASH_AA35C258), ids);
        List<String> versions = annotations.stream().
                map(AnnotationController.AnnotationDTO::version).
                collect(Collectors.toList());
        assertEquals(Arrays.asList("1/2", "1/2", "1/2", "1/2", "1/2", "1/2", "1/2", "1/2", "2/2", "2/2", "2/2"),
                versions);
        assertTrue(annotations.getFirst().description().contains("sunray"));
    }

    @Test
    void testAnnotationAPIWithRevision() {
        final String path = "git/Makefile";
        List<AnnotationController.AnnotationDTO> annotations = target("annotation")
                .queryParam("path", path)
                .queryParam("revision", HASH_BB74B7E8)
                .request()
                .get(new GenericType<>() {
                });
        assertEquals(8, annotations.size());
        assertEquals("Trond Norbye", annotations.getFirst().author());
        Set<String> ids = annotations.stream().
                map(AnnotationController.AnnotationDTO::revision).
                collect(Collectors.toSet());
        List<String> versions = annotations.stream().
                map(AnnotationController.AnnotationDTO::version).
                collect(Collectors.toList());
        assertEquals(Arrays.asList("1/1", "1/1", "1/1", "1/1", "1/1", "1/1", "1/1", "1/1"),
                versions);
        assertEquals(Collections.singleton(HASH_BB74B7E8), ids);
    }

    @Test
    void testAnnotationCorsDeniedOrigin() {
        Response response = target("annotation")
                .queryParam("path", "git/Makefile")
                .request()
                .header(CORS_REQUEST_HEADER, "http://denied.example.com")
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNull(response.getHeaderString(ALLOW_CORS_HEADER));
        assertEquals(CORS_REQUEST_HEADER, response.getHeaderString(VARY_HEADER));
    }
}
