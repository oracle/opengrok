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

import org.apache.lucene.index.Term;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.opengrok.suggest.Suggester;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.CtagsInstalled;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.configuration.SuggesterConfig;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.RestApp;
import org.opengrok.web.api.v1.suggester.provider.filter.AuthorizationFilter;
import org.opengrok.web.api.v1.suggester.provider.service.impl.SuggesterServiceImpl;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@ConditionalRun(CtagsInstalled.class)
public class SuggesterControllerTest extends JerseyTest {

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

    @ClassRule
    public static ConditionalRunRule rule = new ConditionalRunRule();

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static final GenericType<List<Entry<String, Integer>>> popularityDataType =
            new GenericType<List<Entry<String, Integer>>>() {};


    private static TestRepository repository;

    @Override
    protected Application configure() {
        return new RestApp();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        repository = new TestRepository();

        repository.create(SuggesterControllerTest.class.getResourceAsStream("/org/opengrok/indexer/index/source.zip"));

        env.setHistoryEnabled(false);
        env.setProjectsEnabled(true);
        Indexer.getInstance().prepareIndexer(env, true, true,
                Collections.singleton("__all__"),
                false, false, null, null, new ArrayList<>(), false);
        Indexer.getInstance().doIndexerExecution(true, null, null);

        env.getSuggesterConfig().setRebuildCronConfig(null);
    }

    @AfterClass
    public static void tearDownClass() {
        repository.destroy();
    }

    @Before
    public void before() {
        await().atMost(15, TimeUnit.SECONDS).until(() ->
                getSuggesterProjectDataSize() == env.getProjectList().size());

        env.setSuggesterConfig(new SuggesterConfig());
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
        SuggesterConfig config = target(SuggesterController.PATH)
                .path("config")
                .request()
                .get(SuggesterConfig.class);

        assertEquals(env.getSuggesterConfig(), config);
    }

    @Test
    public void testGetSuggestionsSimpleFull() {
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
    public void testGetSuggestionsSimpleDefs() {
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
    public void testGetSuggestionsSimpleRefs() {
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
    public void testGetSuggestionsSimplePath() {
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
    public void testGetSuggestionsBadRequest() {
        Response r = target(SuggesterController.PATH)
                .queryParam("field", "")
                .request()
                .get();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    public void testGetSuggestionsBadRequest2() {
        Response r = target(SuggesterController.PATH)
                .queryParam("field", QueryBuilder.FULL)
                .queryParam("caret", -2)
                .request()
                .get();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    public void testGetSuggestionUnknownField() {
        Response r = target(SuggesterController.PATH)
                .queryParam("field", "unknown")
                .request()
                .get();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    @Test
    public void testGetSuggestionsMultipleProjects() {
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
    public void testGetSuggestionsMultipleProjects2() {
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
    public void testComplexSuggestions() {
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
    public void testWildcard() {
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
    public void testRegex() {
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
    public void testPhraseAfter() {
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
    public void testPhraseBefore() {
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
    public void testPhraseMiddle() {
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
    public void testSloppyPhrase() {
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
    public void testRangeQueryUpper() {
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
    public void testRangeQueryLower() {
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
    public void testComplexSuggestions2() {
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
    public void testInitPopularTermsFromQueries() {
        // terms for prefix t: "text", "texttrim", "tell", "teach", "trimmargin"

        List<String> queries = Arrays.asList(
                "http://localhost:8080/source/search?project=kotlin&q=text",
                "http://localhost:8080/source/search?project=kotlin&q=text",
                "http://localhost:8080/source/search?project=kotlin&q=teach"
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
    public void testInitPopularTermsFromRawDataInvalidRequest() {
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
    public void testDisabledSuggestions() {
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
    public void testMinChars() {
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
    public void testAllowedProjects() {
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
    public void testMaxProjects() {
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
    public void testAllowedFields() {
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
    public void testAllowComplexQueries() {
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
    public void testGetPopularityDataSimple() {
        SuggesterServiceImpl.getInstance().increaseSearchCount("rust", new Term(QueryBuilder.FULL, "main"), 10);

        List<Entry<String, Integer>> res = target(SuggesterController.PATH)
                .path("popularity")
                .path("rust")
                .request()
                .get(popularityDataType);


        assertThat(res, contains(new SimpleEntry<>("main", 10)));
    }

    @Test
    public void testGetPopularityDataAll() {
        SuggesterServiceImpl.getInstance().increaseSearchCount("csharp",
                new Term(QueryBuilder.FULL, "mynamespace"), 10);
        SuggesterServiceImpl.getInstance().increaseSearchCount("csharp",
                new Term(QueryBuilder.FULL, "topclass"), 15);

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
    public void testGetPopularityDataDifferentField() {
        SuggesterServiceImpl.getInstance().increaseSearchCount("swift", new Term(QueryBuilder.FULL, "print"), 10);
        SuggesterServiceImpl.getInstance().increaseSearchCount("swift", new Term(QueryBuilder.DEFS, "greet"), 4);

        List<Entry<String, Integer>> res = target(SuggesterController.PATH)
                .path("popularity")
                .path("swift")
                .queryParam("field", QueryBuilder.DEFS)
                .request()
                .get(popularityDataType);

        assertThat(res, contains(new SimpleEntry<>("greet", 4)));
    }

    @Test
    public void testWildcardQueryEndingWithAsterisk() {
        Result res = target(SuggesterController.PATH)
                .queryParam(AuthorizationFilter.PROJECTS_PARAM, "c")
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "pr?nt*")
                .request()
                .get(Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("print", "printf"));
    }
}
