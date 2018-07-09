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
package org.opensolaris.opengrok.web.suggester.controller;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengrok.suggest.Suggester;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.SuggesterConfig;
import org.opensolaris.opengrok.index.Indexer;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.util.TestRepository;
import org.opensolaris.opengrok.web.suggester.SuggesterApp;
import org.opensolaris.opengrok.web.suggester.model.SuggesterQueryData;
import org.opensolaris.opengrok.web.suggester.provider.filter.AuthorizationFilter;
import org.opensolaris.opengrok.web.suggester.provider.service.impl.SuggesterServiceImpl;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class SuggesterControllerTest extends JerseyTest {

    private static class Result {
        public long time;
        public List<ResultItem> suggestions;
        public String identifier;
        public String queryText;
    }

    private static class ResultItem {
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

    private static TestRepository repository;

    @Override
    protected Application configure() {
        return new SuggesterApp();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        repository = new TestRepository();

        repository.create(SuggesterControllerTest.class.getResourceAsStream("/org/opensolaris/opengrok/index/source.zip"));

        env.setVerbose(false);
        env.setHistoryEnabled(false);
        env.setProjectsEnabled(true);
        Indexer.getInstance().prepareIndexer(env, true, true,
                Collections.singleton("__all__"),
                false, false, null, null, new ArrayList<>(), false);
        Indexer.getInstance().doIndexerExecution(true, null, null);

        env.getConfiguration().getSuggesterConfig().setRebuildCronConfig(null);
    }

    @AfterClass
    public static void tearDownClass() {
        repository.destroy();
    }

    @Before
    public void before() {
        await().atMost(15, TimeUnit.SECONDS).until(() ->
                getSuggesterProjectDataSize() == env.getProjectList().size());
    }

    private static int getSuggesterProjectDataSize() throws Exception {
        Field f = SuggesterServiceImpl.class.getDeclaredField("suggester");
        f.setAccessible(true);
        Suggester suggester = (Suggester) f.get(SuggesterServiceImpl.getInstance());

        Field f2 = Suggester.class.getDeclaredField("projectData");
        f2.setAccessible(true);

        return ((Map) f2.get(suggester)).size();
    }

    @Test
    public void testGetSuggesterConfig() {
        SuggesterConfig config = target("config")
                .request()
                .get(SuggesterConfig.class);

        assertEquals(env.getConfiguration().getSuggesterConfig(), config);
    }

    @Test
    public void testGetSuggestionsSimpleFull() {
        Result res = target()
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "inner")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("innermethod", "innerclass"));
    }

    @Test
    public void testGetSuggestionsSimpleDefs() {
        Result res = target()
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.DEFS)
                .queryParam(QueryBuilder.DEFS, "Inner")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("InnerMethod", "InnerClass"));
    }

    @Test
    public void testGetSuggestionsSimpleRefs() {
        Result res = target()
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java")
                .queryParam("field", QueryBuilder.REFS)
                .queryParam(QueryBuilder.REFS, "Inner")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("InnerMethod", "InnerClass"));
    }

    @Test
    public void testGetSuggestionsSimplePath() {
        Result res = target()
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "c")
                .queryParam("field", QueryBuilder.PATH)
                .queryParam(QueryBuilder.PATH, "he")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                contains("header"));
    }

    @Test
    public void testGetSuggestionsBadRequest() {
        Response r = target()
                .queryParam("field", "")
                .request()
                .get();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    public void testGetSuggestionsBadRequest2() {
        Response r = target()
                .queryParam("field", QueryBuilder.FULL)
                .queryParam("caret", -2)
                .request()
                .get();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    public void testGetSuggestionUnknownField() {
        Response r = target()
                .queryParam("field", "unknown")
                .request()
                .get();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    public void testGetSuggestionsMultipleProjects() {
        Result res = target()
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java", "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "me")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("me", "method", "message", "meta"));
    }

    @Test
    public void testGetSuggestionsMultipleProjects2() {
        Result res = target()
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "java", "kotlin")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "mai")
                .request()
                .get(Result.class);

        assertEquals(1, res.suggestions.size());
        assertThat(res.suggestions.get(0).projects, containsInAnyOrder("java", "kotlin"));
    }

    @Test
    public void testComplexSuggestions() {
        Result res = target()
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
    public void testWildcard() {
        Result res = target()
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
    public void testRegex() {
        Result res = target()
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
    public void testPhraseAfter() {
        Result res = target()
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
    public void testPhraseBefore() {
        Result res = target()
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
    public void testPhraseMiddle() {
        Result res = target()
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
    public void testSloppyPhrase() {
        Result res = target()
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
    public void testRangeQueryUpper() {
        Result res = target()
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
    public void testRangeQueryLower() {
        Result res = target()
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
    public void testComplexSuggestions2() {
        Result res = target()
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
    public void testInitPopularTermsFromQueries() {
        // terms for prefix t: "text", "texttrim", "tell", "teach", "trimmargin"

        List<String> queries = Arrays.asList(
                "http://localhost:8080/source/search?project=kotlin&q=text",
                "http://localhost:8080/source/search?project=kotlin&q=text",
                "http://localhost:8080/source/search?project=kotlin&q=teach"
        );

        target().path("init")
                .path("queries")
                .request()
                .post(Entity.json(queries));

        Result res = target()
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
    public void testInitPopularTermsFromRawData() {
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

        target().path("init")
                .path("raw")
                .request()
                .post(Entity.json(Arrays.asList(data1, data2)));

        Result res = target()
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
    public void testInitPopularTermsFromRawDataInvalidRequest() {
        TermIncrementData data = new TermIncrementData();
        data.project = "kotlin";
        data.field = QueryBuilder.FULL;
        data.token = "array";
        data.increment = -10;

        Response r = target().path("init")
                .path("raw")
                .request()
                .post(Entity.json(Collections.singleton(data)));

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

}
