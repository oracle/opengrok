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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import org.apache.lucene.search.Query;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.search.Hit;
import org.opengrok.indexer.search.SearchEngine;
import org.opengrok.indexer.web.QueryParameters;
import org.opengrok.web.PageConfig;
import org.opengrok.web.api.v1.filter.CorsEnable;
import org.opengrok.web.api.v1.suggester.provider.service.SuggesterService;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Path(SearchController.PATH)
public class SearchController {

    public static final String PATH = "search";

    private static final int MAX_RESULTS = 1000;

    @Inject
    private SuggesterService suggester;

    @GET
    @CorsEnable
    @Produces(MediaType.APPLICATION_JSON)
    public SearchResult search(
            @Context final HttpServletRequest req,
            @QueryParam(QueryParameters.FULL_SEARCH_PARAM) final String full,
            @QueryParam("def") final String def, // Nearly QueryParameters.DEFS_SEARCH_PARAM
            @QueryParam("symbol") final String symbol, // Akin to QueryBuilder.REFS_SEARCH_PARAM
            @QueryParam(QueryParameters.PATH_SEARCH_PARAM) final String path,
            @QueryParam(QueryParameters.HIST_SEARCH_PARAM) final String hist,
            @QueryParam(QueryParameters.TYPE_SEARCH_PARAM) final String type,
            @QueryParam("projects") final List<String> projects,
            @QueryParam("maxresults") // Akin to QueryParameters.COUNT_PARAM
            @DefaultValue(MAX_RESULTS + "") final int maxResults,
            @QueryParam(QueryParameters.START_PARAM) @DefaultValue(0 + "") final int startDocIndex
    ) {
        try (SearchEngineWrapper engine = new SearchEngineWrapper(full, def, symbol, path, hist, type)) {

            if (!engine.isValid()) {
                throw new WebApplicationException("Invalid request", Response.Status.BAD_REQUEST);
            }

            Instant startTime = Instant.now();

            suggester.onSearch(projects, engine.getQuery());

            Map<String, List<SearchHit>> hits = engine.search(req, projects, startDocIndex, maxResults)
                    .stream()
                    .collect(Collectors.groupingBy(Hit::getPath,
                            Collectors.mapping(h -> new SearchHit(h.getLine(), h.getLineno()), Collectors.toList())));

            long duration = Duration.between(startTime, Instant.now()).toMillis();

            int endDocument = startDocIndex + hits.size() - 1;

            return new SearchResult(duration, engine.numResults, hits, startDocIndex, endDocument);
        }
    }

    private static class SearchEngineWrapper implements AutoCloseable {

        private SearchEngine engine = new SearchEngine();

        private int numResults;

        private SearchEngineWrapper(
                final String full,
                final String def,
                final String symbol,
                final String path,
                final String hist,
                final String type
        ) {
            engine.setFreetext(full);
            engine.setDefinition(def);
            engine.setSymbol(symbol);
            engine.setFile(path);
            engine.setHistory(hist);
            engine.setType(type);
        }

        public List<Hit> search(
                final HttpServletRequest req,
                final List<String> projects,
                final int startDocIndex,
                final int maxResults
        ) {
            Set<Project> allProjects = PageConfig.get(req).getProjectHelper().getAllProjects();
            if (projects == null || projects.isEmpty()) {
                numResults = engine.search(new ArrayList<>(allProjects));
            } else {
                numResults = engine.search(allProjects.stream()
                        .filter(p -> projects.contains(p.getName()))
                        .collect(Collectors.toList()));
            }

            if (startDocIndex > numResults) {
                return Collections.emptyList();
            }

            int resultSize = numResults - startDocIndex;
            if (resultSize > maxResults) {
                resultSize = maxResults;
            }

            List<Hit> results = new ArrayList<>();
            engine.results(startDocIndex, startDocIndex + resultSize, results);

            return results;
        }

        private boolean isValid() {
            return engine.isValidQuery();
        }

        private Query getQuery() {
            return engine.getQueryObject();
        }

        @Override
        public void close() {
            engine.destroy();
        }
    }

    private static class SearchResult {

        private final long time;

        private final int resultCount;

        private final int startDocument;

        private final int endDocument;

        private final Map<String, List<SearchHit>> results;

        private SearchResult(
                final long time,
                final int resultCount,
                final Map<String, List<SearchHit>> results,
                final int startDocument,
                final int endDocument
        ) {
            this.time = time;
            this.resultCount = resultCount;
            this.results = results;
            this.startDocument = startDocument;
            this.endDocument = endDocument;
        }

        public long getTime() {
            return time;
        }

        public int getResultCount() {
            return resultCount;
        }

        public Map<String, List<SearchHit>> getResults() {
            return results;
        }

        public int getStartDocument() {
            return startDocument;
        }

        public int getEndDocument() {
            return endDocument;
        }
    }

    private static class SearchHit {

        private final String line;

        private final String lineNumber;

        private SearchHit(final String line, final String lineNumber) {
            this.line = line;
            this.lineNumber = lineNumber;
        }

        public String getLine() {
            return line;
        }

        public String getLineNumber() {
            return lineNumber;
        }
    }

}
