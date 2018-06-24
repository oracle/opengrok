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
package org.opensolaris.opengrok.web.api.controller;

import org.opensolaris.opengrok.search.Hit;
import org.opensolaris.opengrok.search.SearchEngine;

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
import java.util.stream.Collectors;

@Path("/search")
public class SearchController {

    private static final int MAX_RESULTS = 1000;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public SearchResult search(
            @Context final HttpServletRequest req,
            @QueryParam("freetext") final String freetext,
            @QueryParam("def") final String def,
            @QueryParam("symbol") final String symbol,
            @QueryParam("path") final String path,
            @QueryParam("hist") final String hist,
            @QueryParam("type") final String type,
            @QueryParam("projects") final List<String> projects,
            @QueryParam("maxresults") @DefaultValue(MAX_RESULTS + "") final int maxResults,
            @QueryParam("start") @DefaultValue(0 + "") final int startIndex
    ) {
        try (SearchEngineWrapper engine = new SearchEngineWrapper(freetext, def, symbol, path, hist, type)) {

            if (!engine.isValid()) {
                throw new WebApplicationException("Invalid request", Response.Status.BAD_REQUEST);
            }

            Instant startTime = Instant.now();

            List<SearchHit> hits = engine.search(req, projects, startIndex, maxResults)
                    .stream()
                    .map(hit -> new SearchHit(hit.getLine(), hit.getPath()))
                    .collect(Collectors.toList());

            long duration = Duration.between(startTime, Instant.now()).toMillis();

            return new SearchResult(duration, engine.numResults, hits, startIndex, startIndex + hits.size());
        }
    }

    private static class SearchEngineWrapper implements AutoCloseable {

        private SearchEngine engine = new SearchEngine();

        private int numResults;

        private SearchEngineWrapper(
                final String freetext,
                final String def,
                final String symbol,
                final String path,
                final String hist,
                final String type
        ) {
            engine.setFreetext(freetext);
            engine.setDefinition(def);
            engine.setSymbol(symbol);
            engine.setFile(path);
            engine.setHistory(hist);
            engine.setType(type);
        }

        public List<Hit> search(
                final HttpServletRequest req,
                final List<String> projects,
                final int startIndex,
                final int maxResults
        ) {
            if (projects == null || projects.isEmpty()) {
                numResults = engine.search(req);
            } else {
                numResults = engine.search(req, projects.toArray(new String[0]));
            }

            if (startIndex > numResults) {
                return Collections.emptyList();
            }

            int resultSize = numResults - startIndex;
            if (resultSize > maxResults) {
                resultSize = maxResults;
            }

            List<Hit> results = new ArrayList<>(resultSize);
            engine.results(startIndex, startIndex + resultSize, results);

            return results;
        }

        private boolean isValid() {
            return engine.isValidQuery();
        }

        @Override
        public void close() {
            engine.destroy();
        }
    }

    private static class SearchResult {

        private final long duration;

        private final int resultCount;

        private final int startIndex;

        private final int endIndex;

        private final List<SearchHit> results;

        private SearchResult(
                final long duration,
                final int resultCount,
                final List<SearchHit> results,
                final int startIndex,
                final int endIndex
        ) {
            this.duration = duration;
            this.resultCount = resultCount;
            this.results = results;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        public long getDuration() {
            return duration;
        }

        public int getResultCount() {
            return resultCount;
        }

        public List<SearchHit> getResults() {
            return results;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public int getEndIndex() {
            return endIndex;
        }
    }

    private static class SearchHit {

        private final String line;

        private final String path;

        private SearchHit(final String line, final String path) {
            this.line = line;
            this.path = path;
        }

        public String getLine() {
            return line;
        }

        public String getPath() {
            return path;
        }
    }

}
