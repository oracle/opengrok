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
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import org.apache.lucene.index.Term;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.ServletDeploymentContext;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.configuration.SuggesterConfig;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.RestApp;
import org.opengrok.web.api.v1.suggester.provider.filter.AuthorizationFilter;
import org.opengrok.web.api.v1.suggester.provider.service.impl.SuggesterServiceImpl;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.web.api.v1.filter.CorsFilter.ALLOW_CORS_HEADER;
import static org.opengrok.web.api.v1.filter.CorsFilter.CORS_REQUEST_HEADER;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class SuggesterControllerTest extends OGKJerseyTest {

    public static class Result {
        public long time;
        public List<ResultItem> suggestions;
        public String identifier;
        public String queryText;
        public boolean partialResult;
    }

    public static class ResultItem {
        public String phrase;
        public Set<String> projects;
        public long score;
    }

    private static class TermIncrementData {
        public String project;
        public String field;
        public String token;
        public int increment;
    }

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static final GenericType<List<Entry<String, Integer>>> popularityDataType = new GenericType<>() {
    };


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

        repository.create(SuggesterControllerTest.class.getClassLoader().getResource("sources"));

        env.setHistoryEnabled(false);
        env.setProjectsEnabled(true);
        Indexer.getInstance().prepareIndexer(env, true, true,
                null, null);
        env.setDefaultProjectsFromNames(Collections.singleton("__all__"));
        Indexer.getInstance().doIndexerExecution(null, null);

        env.getSuggesterConfig().setRebuildCronConfig(null);
    }

    @AfterAll
    public static void tearDownClass() {
        repository.destroy();
    }

    @BeforeEach
    public void before() throws InterruptedException {
        SuggesterServiceImpl.getInstance().waitForInit(15, TimeUnit.SECONDS);

        env.setSuggesterConfig(new SuggesterConfig());
    }

    @Test
    void testGetSuggesterConfig() {
        SuggesterConfig config = target(SuggesterController.PATH)
                .path("config")
                .request()
                .get(SuggesterConfig.class);

        assertEquals(env.getSuggesterConfig(), config);
    }

    @Test
    void testGetSuggesterConfigCors() {
        Response response = target(SuggesterController.PATH)
                .path("config")
                .request()
                .header(CORS_REQUEST_HEADER, "http://example.com")
                .get();
        assertEquals("*", response.getHeaderString(ALLOW_CORS_HEADER));
    }

    @Test
    void testGetSuggestionsSimpleFull() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "inner")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("innermethod", "innerclass"));
    }

    @Test
    void testGetSuggestionsCors() {
        Response response = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "inner")
                .request()
                .header(CORS_REQUEST_HEADER, "http://example.com")
                .get();

        assertEquals("*", response.getHeaderString(ALLOW_CORS_HEADER));
    }

    @Test
    void testGetSuggestionsSimpleDefs() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.DEFS)
                .queryParam(QueryBuilder.DEFS, "Inner")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("InnerMethod", "InnerClass"));
    }

    @Test
    void testGetSuggestionsSimpleRefs() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.REFS)
                .queryParam(QueryBuilder.REFS, "Inner")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("InnerMethod", "InnerClass"));
    }

    @Test
    void testGetSuggestionsSimplePath() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "c")
                .queryParam("field", QueryBuilder.PATH)
                .queryParam(QueryBuilder.PATH, "he")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                contains("header"));
    }

    @Test
    void testGetSuggestionsBadRequest() {
        Response r = target(SuggesterController.PATH)
                .queryParam("field", "")
                .request()
                .get();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void testGetSuggestionsBadRequest2() {
        Response r = target(SuggesterController.PATH)
                .queryParam("field", QueryBuilder.FULL)
                .queryParam("caret", -2)
                .request()
                .get();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void testGetSuggestionUnknownField() {
        Response r = target(SuggesterController.PATH)
                .queryParam("field", "unknown")
                .request()
                .get();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void testGetSuggestionsMultipleProjects() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java", "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "me")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("me", "method", "message", "meta"));
    }

    @Test
    void testGetSuggestionsMultipleProjects2() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java", "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "mai")
                .request()
                .get(Result.class);

        assertEquals(1, res.suggestions.size());
        assertThat(res.suggestions.get(0).projects, containsInAnyOrder("java", "kotlin"));
    }

    @Test
    void testComplexSuggestions() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "s")
                .queryParam(QueryBuilder.PATH, "bug15890")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("since"));
    }

    @Test
    void testWildcard() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "b?")
                .queryParam(QueryBuilder.PATH, "sample")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                contains("by"));
    }

    @Test
    void testRegex() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "/b./")
                .queryParam(QueryBuilder.PATH, "sample")
                .queryParam("caret", 1)
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                contains("by"));
    }

    @Test
    void testPhraseAfter() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "\"contents of this \"")
                .queryParam(QueryBuilder.PATH, "sample")
                .queryParam("caret", 18)
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                contains("file"));
    }

    @Test
    void testPhraseBefore() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "\" contents of this\"")
                .queryParam(QueryBuilder.PATH, "sample")
                .queryParam("caret", 1)
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                contains("the"));
    }

    @Test
    void testPhraseMiddle() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "\"contents  this\"")
                .queryParam(QueryBuilder.PATH, "sample")
                .queryParam("caret", 10)
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                contains("of"));
    }

    @Test
    void testSloppyPhrase() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "\"contents of this \"~1")
                .queryParam(QueryBuilder.PATH, "sample")
                .queryParam("caret", 18)
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("file", "are"));
    }

    @Test
    void testRangeQueryUpper() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "{templ}").resolveTemplate("templ", "{main TO m}")
                .queryParam("caret", 10)
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("me", "mutablelistof"));
    }

    @Test
    void testRangeQueryLower() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "{templ}").resolveTemplate("templ", "{m TO mutablelistof}")
                .queryParam("caret", 1)
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("main", "me"));
    }

    @Test
    void testComplexSuggestions2() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "me m")
                .queryParam("caret", 4)
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("main", "mutablelistof"));
    }

    @Test
    void testInitPopularTermsFromQueries() {
        // terms for prefix t: "text", "texttrim", "tell", "teach", "trimmargin"

        List<String> queries = Arrays.asList(
                "http://localhost:8080/source/search?project=kotlin&full=text",
                "http://localhost:8080/source/search?project=kotlin&full=text",
                "http://localhost:8080/source/search?project=kotlin&full=teach"
        );

        target(SuggesterController.PATH)
                .path("init")
                .path("queries")
                .request()
                .post(Entity.json(queries));

        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "t")
                .queryParam("caret", 1)
                .queryParam(QueryBuilder.PATH, "kt")
                .request()
                .get(Result.class);

        List<String> suggestions = res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList());

        assertEquals("text", suggestions.get(0));
        assertEquals("teach", suggestions.get(1));
    }

    @Test
    void testInitPopularTermsFromRawData() {
        // terms for prefix a: "args", "array", "and"

        TermIncrementData data1 = new TermIncrementData();
        data1.project = "kotlin";
        data1.field = QueryBuilder.FULL;
        data1.token = "args";
        data1.increment = 100;

        TermIncrementData data2 = new TermIncrementData();
        data2.project = "kotlin";
        data2.field = QueryBuilder.FULL;
        data2.token = "array";
        data2.increment = 50;

        target(SuggesterController.PATH)
                .path("init")
                .path("raw")
                .request()
                .post(Entity.json(Arrays.asList(data1, data2)));

        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "a")
                .queryParam("caret", 1)
                .queryParam(QueryBuilder.PATH, "kt")
                .request()
                .get(Result.class);

        List<String> suggestions = res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList());

        assertEquals("args", suggestions.get(0));
        assertEquals("array", suggestions.get(1));
    }

    @Test
    void testInitPopularTermsFromRawDataInvalidRequest() {
        TermIncrementData data = new TermIncrementData();
        data.project = "kotlin";
        data.field = QueryBuilder.FULL;
        data.token = "array";
        data.increment = -10;

        Response r = target(SuggesterController.PATH)
                .path("init")
                .path("raw")
                .request()
                .post(Entity.json(Collections.singleton(data)));

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    void testDisabledSuggestions() {
        env.getSuggesterConfig().setEnabled(false);

        Response r = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "inner")
                .request()
                .get();

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    @Test
    void testMinChars() {
        env.getSuggesterConfig().setMinChars(2);

        Response r = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "i")
                .request()
                .get();

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    @Test
    void testAllowedProjects() {
        env.getSuggesterConfig().setAllowedProjects(Collections.singleton("kotlin"));

        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java", "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "me")
                .request()
                .get(Result.class);

        // only terms from kotlin project are expected
        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("me"));
    }

    @Test
    void testMaxProjects() {
        env.getSuggesterConfig().setMaxProjects(1);

        Response r = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java", "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "me")
                .request()
                .get();

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    @Test
    void testAllowedFields() {
        env.getSuggesterConfig().setAllowedFields(Collections.singleton(QueryBuilder.DEFS));

        Response r = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java", "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "me")
                .request()
                .get();

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    @Test
    void testAllowComplexQueries() {
        env.getSuggesterConfig().setAllowComplexQueries(false);

        Response r = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java", "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "me")
                .queryParam(QueryBuilder.PATH, "kt")
                .request()
                .get();

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), r.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked") // for contains
    void testGetPopularityDataSimple() {
        assertTrue(SuggesterServiceImpl.getInstance().increaseSearchCount("rust",
                new Term(QueryBuilder.FULL, "main"), 10, true));

        List<Entry<String, Integer>> res = target(SuggesterController.PATH)
                .path("popularity")
                .path("rust")
                .request()
                .get(popularityDataType);

        assertThat(res, contains(new SimpleEntry<>("main", 10)));
    }

    @Test
    @SuppressWarnings("unchecked") // for contains
    void testGetPopularityDataAll() {
        assertTrue(SuggesterServiceImpl.getInstance().increaseSearchCount("csharp",
                new Term(QueryBuilder.FULL, "mynamespace"), 10, true));
        assertTrue(SuggesterServiceImpl.getInstance().increaseSearchCount("csharp",
                new Term(QueryBuilder.FULL, "topclass"), 15, true));

        List<Entry<String, Integer>> res = target(SuggesterController.PATH)
                .path("popularity")
                .path("csharp")
                .queryParam("pageSize", 1)
                .queryParam("all", true)
                .request()
                .get(popularityDataType);

        assertThat(res, contains(new SimpleEntry<>("topclass", 15), new SimpleEntry<>("mynamespace", 10)));
    }

    @Test
    @SuppressWarnings("unchecked") // for contains
    void testGetPopularityDataDifferentField() {
        assertTrue(SuggesterServiceImpl.getInstance().increaseSearchCount("swift",
                new Term(QueryBuilder.FULL, "print"), 10, true));
        assertTrue(SuggesterServiceImpl.getInstance().increaseSearchCount("swift",
                new Term(QueryBuilder.DEFS, "greet"), 4, true));

        List<Entry<String, Integer>> res = target(SuggesterController.PATH)
                .path("popularity")
                .path("swift")
                .queryParam("field", QueryBuilder.DEFS)
                .request()
                .get(popularityDataType);

        assertThat(res, contains(new SimpleEntry<>("greet", 4)));
    }

    @Test
    void testWildcardQueryEndingWithAsterisk() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "c")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "pr?nt*")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("print", "printf"));
    }

    @Test
    void zTestRebuild() throws InterruptedException {
        Response res = target(SuggesterController.PATH)
                .path("rebuild")
                .request()
                .put(Entity.text(""));

        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), res.getStatus());
        SuggesterServiceImpl.getInstance().waitForRebuild(15, TimeUnit.SECONDS);
    }

    @Test
    void zTestRebuildProject() throws InterruptedException {
        Response res = target(SuggesterController.PATH)
                .path("rebuild")
                .path("c")
                .request()
                .put(Entity.text(""));

        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), res.getStatus());
        SuggesterServiceImpl.getInstance().waitForRebuild(15, TimeUnit.SECONDS);
    }
}
