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
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.opengrok.indexer.web.Laundromat;
import org.opengrok.suggest.LookupResultItem;
import org.opengrok.suggest.Suggester.Suggestions;
import org.opengrok.suggest.SuggesterUtils;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.configuration.SuggesterConfig;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.web.Util;
import org.opengrok.web.api.v1.filter.CorsEnable;
import org.opengrok.web.api.v1.filter.IncomingFilter;
import org.opengrok.web.api.v1.suggester.model.SuggesterData;
import org.opengrok.web.api.v1.suggester.model.SuggesterQueryData;
import org.opengrok.web.api.v1.suggester.parser.SuggesterQueryDataParser;
import org.opengrok.web.api.v1.suggester.provider.filter.Authorized;
import org.opengrok.web.api.v1.suggester.provider.filter.Suggester;
import org.opengrok.web.api.v1.suggester.provider.service.SuggesterService;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Endpoint for suggester related REST queries.
 */
@Path(SuggesterController.PATH)
@Suggester
public final class SuggesterController {

    public static final String PATH = "suggest";

    private static final int POPULARITY_DEFAULT_PAGE_SIZE = 100;

    private static final Logger logger = LoggerFactory.getLogger(SuggesterController.class);

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private final SuggesterService suggester;

    @Inject
    public SuggesterController(SuggesterService suggester) {
        this.suggester = suggester;
    }

    /**
     * Returns suggestions based on the search criteria specified in {@code data}.
     * @param data suggester form data
     * @return list of suggestions and other related information
     * @throws ParseException if the Lucene query created from {@code data} could not be parsed
     */
    @GET
    @Authorized
    @CorsEnable
    @Produces(MediaType.APPLICATION_JSON)
    public Result getSuggestions(@Valid @BeanParam final SuggesterQueryData data) throws ParseException {
        Instant start = Instant.now();

        SuggesterData suggesterData = SuggesterQueryDataParser.parse(data);
        if (suggesterData.getSuggesterQuery() == null) {
            throw new ParseException("Could not determine suggester query");
        }

        SuggesterConfig config = env.getSuggesterConfig();

        modifyDataBasedOnConfiguration(suggesterData, config);

        if (!satisfiesConfiguration(suggesterData, config)) {
            logger.log(Level.FINER, "Suggester request with data {0} does not satisfy configuration settings", data);
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        Suggestions suggestions = suggester.getSuggestions(
                suggesterData.getProjects(), suggesterData.getSuggesterQuery(), suggesterData.getQuery());

        Instant end = Instant.now();

        long timeInMs = Duration.between(start, end).toMillis();

        return new Result(suggestions.getItems(), suggesterData.getIdentifier(),
                suggesterData.getSuggesterQueryFieldText(), timeInMs, suggestions.isPartialResult());
    }

    private void modifyDataBasedOnConfiguration(final SuggesterData data, final SuggesterConfig config) {
        if (config.getAllowedProjects() != null) {
            data.getProjects().removeIf(project -> !config.getAllowedProjects().contains(project));
        }
    }

    private boolean satisfiesConfiguration(final SuggesterData data, final SuggesterConfig config) {
        if (config.getMinChars() > data.getSuggesterQuery().length()) {
            return false;
        }

        if (config.getMaxProjects() < data.getProjects().size()) {
            return false;
        }

        if (config.getAllowedFields() != null && !config.getAllowedFields().contains(data.getSuggesterQuery().getField())) {
            return false;
        }

        return config.isAllowComplexQueries() || !SuggesterUtils.isComplexQuery(data.getQuery(), data.getSuggesterQuery());
    }

    /**
     * Returns the suggester configuration {@link SuggesterConfig}.
     * Because of the {@link IncomingFilter}, the
     * {@link org.opengrok.web.api.v1.controller.ConfigurationController} cannot be accessed from the
     * web page by the remote user. To resolve the problem, this method exposes this functionality.
     * @return suggester configuration
     */
    @GET
    @Path("/config")
    @CorsEnable
    @Produces(MediaType.APPLICATION_JSON)
    public SuggesterConfig getConfig() {
        return env.getSuggesterConfig();
    }

    @PUT
    @Path("/rebuild")
    public void rebuild() {
        CompletableFuture.runAsync(suggester::rebuild);
    }

    @PUT
    @Path("/rebuild/{project}")
    public void rebuild(@PathParam("project") final String project) {
        CompletableFuture.runAsync(() -> suggester.rebuild(project));
    }

    /**
     * Initializes the search data used by suggester to perform most popular completion. The passed {@code urls} are
     * decomposed into single terms which search counts are then increased by 1.
     * @param urls list of URLs in JSON format, e.g.
     * {@code ["http://demo.opengrok.org/search?project=opengrok&full=test"]}
     */
    @POST
    @Path("/init/queries")
    @Consumes(MediaType.APPLICATION_JSON)
    public void addSearchCountsQueries(final List<String> urls) {
        for (String urlStr : urls) {
            try {
                var url = new URL(urlStr);
                var params = Util.getQueryParams(url);

                var projects = params.get("project");

                for (String field : QueryBuilder.getSearchFields()) {

                    List<String> fieldQueryText = params.get(field);
                    if (Objects.nonNull(fieldQueryText) && !fieldQueryText.isEmpty()) {
                        if (fieldQueryText.size() > 2) {
                            logger.log(Level.WARNING, "Bad format, ignoring {0}", urlStr);
                        } else {
                            getQuery(field, fieldQueryText.get(0))
                                    .ifPresent(q -> suggester.onSearch(projects, q));

                        }
                    }

                }
            } catch (MalformedURLException e) {
                logger.log(Level.WARNING, e, () -> "Could not add search counts for " + urlStr);
            }
        }
    }

    private Optional<Query> getQuery(final String field, final String value) {
        QueryBuilder builder = new QueryBuilder();

        switch (field) {
            case QueryBuilder.FULL:
                builder.setFreetext(value);
                break;
            case QueryBuilder.DEFS:
                builder.setDefs(value);
                break;
            case QueryBuilder.REFS:
                builder.setRefs(value);
                break;
            case QueryBuilder.PATH:
                builder.setPath(value);
                break;
            case QueryBuilder.HIST:
                builder.setHist(value);
                break;
            case QueryBuilder.TYPE:
                builder.setType(value);
                break;
            default:
                return Optional.empty();
        }
        try {
            return Optional.of(builder.build());
        } catch (ParseException e) {
            logger.log(Level.FINE, "Bad request", e);
            return Optional.empty();
        }
    }

    /**
     * Initializes the search data used by suggester to perform most popular completion.
     * @param termIncrements data by which to initialize the search data
     */
    @POST
    @Path("/init/raw")
    @Consumes(MediaType.APPLICATION_JSON)
    public void addSearchCountsRaw(@Valid final List<TermIncrementData> termIncrements) {
        for (TermIncrementData termIncrement : termIncrements) {
            suggester.increaseSearchCount(termIncrement.project,
                    new Term(termIncrement.field, termIncrement.token), termIncrement.increment);
        }
    }

    /**
     * Returns the searched terms sorted according to their popularity.
     * @param project project for which to return the data
     * @param field field for which to return the data
     * @param page which page of data to retrieve
     * @param pageSize number of results to return
     * @param all return all pages
     * @return list of terms with their popularity
     */
    @GET
    @Path("/popularity/{project}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Entry<String, Integer>> getPopularityDataPaged(
            @PathParam("project") String project,
            @QueryParam("field") @DefaultValue(QueryBuilder.FULL) String field,
            @QueryParam("page") @DefaultValue("" + 0) final int page,
            @QueryParam("pageSize") @DefaultValue("" + POPULARITY_DEFAULT_PAGE_SIZE) final int pageSize,
            @QueryParam("all") final boolean all
    ) {
        if (!QueryBuilder.isSearchField(field)) {
            throw new WebApplicationException("field is invalid", Response.Status.BAD_REQUEST);
        }
        // Avoid classification as a taint bug.
        project = Laundromat.launderInput(project);
        field = Laundromat.launderInput(field);

        List<Entry<BytesRef, Integer>> data;
        if (all) {
            data = suggester.getPopularityData(project, field, 0, Integer.MAX_VALUE);
        } else {
            data = suggester.getPopularityData(project, field, page, pageSize);
        }
        return data.stream()
                .map(e -> new SimpleEntry<>(e.getKey().utf8ToString(), e.getValue()))
                .collect(Collectors.toList());
    }

    private static class Result {

        private long time;

        private List<LookupResultItem> suggestions;

        private String identifier;

        private String queryText;

        private boolean partialResult;

        Result(
                final List<LookupResultItem> suggestions,
                final String identifier,
                final String queryText,
                final long time,
                final boolean partialResult
        ) {
            this.suggestions = suggestions;
            this.identifier = identifier;
            this.queryText = queryText;
            this.time = time;
            this.partialResult = partialResult;
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }

        public List<LookupResultItem> getSuggestions() {
            return suggestions;
        }

        public void setSuggestions(List<LookupResultItem> suggestions) {
            this.suggestions = suggestions;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public String getQueryText() {
            return queryText;
        }

        public void setQueryText(String queryText) {
            this.queryText = queryText;
        }

        public boolean isPartialResult() {
            return partialResult;
        }

        public void setPartialResult(boolean partialResult) {
            this.partialResult = partialResult;
        }
    }

    private static class TermIncrementData {

        private String project;

        @NotBlank(message = "Field cannot be blank")
        private String field;

        @NotBlank(message = "Token cannot be blank")
        private String token;

        @Min(message = "Increment must be positive", value = 0)
        private int increment;

        public String getProject() {
            return project;
        }

        public void setProject(String project) {
            this.project = project;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public int getIncrement() {
            return increment;
        }

        public void setIncrement(int increment) {
            this.increment = increment;
        }
    }

}
