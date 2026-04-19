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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.web.QueryParameters;
import org.opengrok.web.api.v1.RestApp;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.web.api.v1.filter.CorsFilter.ALLOW_CORS_HEADER;
import static org.opengrok.web.api.v1.filter.CorsFilter.CORS_REQUEST_HEADER;

class SearchControllerTest extends OGKJerseyTest {

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static TestRepository repository;

    @Override
    protected DeploymentContext configureDeployment() {
        return ServletDeploymentContext.forServlet(new ServletContainer(new RestApp())).build();
    }

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new GrizzlyWebTestContainerFactory();
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true"); // necessary to test CORS from controllers
        repository = new TestRepository();
        URL url = SearchControllerTest.class.getClassLoader().getResource("sources");
        assertNotNull(url);
        repository.create(url);

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setHistoryEnabled(false);
        env.setProjectsEnabled(true);
        env.setDefaultProjectsFromNames(Collections.singleton("__all__"));
        env.getSuggesterConfig().setRebuildCronConfig(null);
        RepositoryFactory.initializeIgnoredNames(env);

        Indexer.getInstance().prepareIndexer(env, true, true, null, null);
        Indexer.getInstance().doIndexerExecution(null, null);
    }

    @AfterAll
    public static void tearDownClass() {
        repository.destroy();
    }

    @Test
    void testSearchCors() {
        Response response = target(SearchController.PATH)
                .request()
                .header(CORS_REQUEST_HEADER, "http://example.com")
                .get();
        assertEquals("*", response.getHeaderString(ALLOW_CORS_HEADER));
    }

    @Test
    void testSearchReturnsResults() {
        // "dump" is a method name defined in java/Main.java — verifies the index is live
        // and the API returns a well-formed response.
        GenericType<Map<String, Object>> type = new GenericType<>() { };
        Response response = target(SearchController.PATH)
                .queryParam(QueryParameters.FULL_SEARCH_PARAM, "dump")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Map<String, Object> body = response.readEntity(type);
        Object resultsObj = body.get("results");
        assertNotNull(resultsObj);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> results =
                (Map<String, List<Map<String, Object>>>) resultsObj;
        assertFalse(results.isEmpty());
    }

    @Test
    void testSearchResultsPreserveHitOrder() {
        // Results presented by the search API must be stable w.r.t. file ordering across
        // repeated calls, while preserving Lucene scoring order.
        GenericType<Map<String, Object>> type = new GenericType<>() { };
        String firstKey1 = getFirstResultKey(type);
        String firstKey2 = getFirstResultKey(type);
        assertNotNull(firstKey1);
        assertEquals(firstKey1, firstKey2);
    }

    private String getFirstResultKey(GenericType<Map<String, Object>> type) {
        Response response = target(SearchController.PATH)
                .queryParam(QueryParameters.FULL_SEARCH_PARAM, "dump")
                .request()
                .get();
        Map<String, Object> body = response.readEntity(type);
        @SuppressWarnings("unchecked")
        Map<String, ?> results = (Map<String, ?>) body.get("results");
        if (results == null || results.isEmpty()) {
            return null;
        }
        return results.keySet().iterator().next();
    }

    @Test
    void testSearchResultsHaveLineNumbers() {
        // maxhitsperfile defaults to 0 (unlimited) so all per-file hits carry a lineNumber.
        // Restrict to Java source type to exclude XREFABLE binary files (jar, class, elf)
        // whose hits go through the Summarizer path and never carry a lineNumber.
        GenericType<Map<String, Object>> type = new GenericType<>() { };
        Response response = target(SearchController.PATH)
                .queryParam(QueryParameters.FULL_SEARCH_PARAM, "dump")
                .queryParam(QueryParameters.TYPE_SEARCH_PARAM, "java")
                .request()
                .get();
        Map<String, Object> body = response.readEntity(type);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> results =
                (Map<String, List<Map<String, Object>>>) body.get("results");
        assertNotNull(results);
        assertFalse(results.isEmpty());
        results.values().forEach(hits ->
                hits.forEach(hit -> {
                    assertNotNull(hit.get("lineNumber"));
                    assertFalse(hit.get("lineNumber").toString().isEmpty());
                })
        );
    }

    /**
     * Verify that resultCount reflects the true total number of matching documents and
     * endDocument is derived from the document page size, not from the number of unique
     * file paths in the grouped results map.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testResultCountAndEndDocument() {
        int maxResults = 100;
        GenericType<Map<String, Object>> type = new GenericType<>() { };
        Response response = target(SearchController.PATH)
                .queryParam(QueryParameters.FULL_SEARCH_PARAM, "main")
                .queryParam("maxresults", maxResults)
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        Map<String, Object> json = response.readEntity(type);
        int resultCount = (int) json.get("resultCount");
        int startDocument = (int) json.get("startDocument");
        int endDocument = (int) json.get("endDocument");

        assertTrue(resultCount > 0, "Search for 'main' should return results");

        int expectedPageSize = Math.min(maxResults, resultCount - startDocument);
        int expectedEndDocument = expectedPageSize > 0
                ? startDocument + expectedPageSize - 1
                : startDocument;
        assertEquals(expectedEndDocument, endDocument,
                "endDocument must reflect the document page size, not the number of unique file paths");
    }
}
