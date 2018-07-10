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

import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.hibernate.validator.constraints.NotEmpty;
import org.opengrok.suggest.LookupResultItem;
import org.opengrok.suggest.SuggesterUtils;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.SuggesterConfig;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.web.Util;
import org.opensolaris.opengrok.web.suggester.model.SuggesterData;
import org.opensolaris.opengrok.web.suggester.model.SuggesterQueryData;
import org.opensolaris.opengrok.web.suggester.parser.SuggesterQueryDataParser;
import org.opensolaris.opengrok.web.suggester.provider.filter.Authorized;
import org.opensolaris.opengrok.web.suggester.provider.filter.Localhost;
import org.opensolaris.opengrok.web.suggester.provider.service.SuggesterService;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Endpoint for suggester related REST queries.
 */
@Path("/")
public final class SuggesterController {

    private static final Logger logger = LoggerFactory.getLogger(SuggesterController.class);

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Inject
    private SuggesterService suggester;

    /**
     * Returns suggestions based on the search criteria specified in {@code data}.
     * @param data suggester form data
     * @return list of suggestions and other related information
     * @throws ParseException if the Lucene query created from {@code data} could not be parsed
     */
    @GET
    @Authorized
    @Produces(MediaType.APPLICATION_JSON)
    public Result getSuggestions(@Valid @BeanParam final SuggesterQueryData data) throws ParseException {
        Instant start = Instant.now();

        SuggesterData suggesterData = SuggesterQueryDataParser.parse(data);
        if (suggesterData.getSuggesterQuery() == null) {
            throw new ParseException("Could not determine suggester query");
        }

        SuggesterConfig config = env.getConfiguration().getSuggesterConfig();

        modifyDataBasedOnConfiguration(suggesterData, config);

        if (!satisfiesConfiguration(suggesterData, config)) {
            logger.log(Level.FINER, "Suggester request with data {0} does not satisfy configuration settings", data);
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        List<LookupResultItem> suggestedItems = suggester.getSuggestions(
                suggesterData.getProjects(), suggesterData.getSuggesterQuery(), suggesterData.getQuery());

        Instant end = Instant.now();

        long timeInMs = Duration.between(start, end).toMillis();

        return new Result(suggestedItems, suggesterData.getIdentifier(), suggesterData.getSuggesterQueryFieldText(), timeInMs);
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

        if (!config.isAllowComplexQueries() && SuggesterUtils.isComplexQuery(data.getQuery(), data.getSuggesterQuery())) {
            return false;
        }

        return true;
    }

    /**
     * Returns the suggester configuration {@link SuggesterConfig}.
     * Because of the {@link org.opensolaris.opengrok.web.api.v1.filter.LocalhostFilter}, the
     * {@link org.opensolaris.opengrok.web.api.v1.controller.ConfigurationController} cannot be accessed from the
     * web page by the remote user. To resolve the problem, this method exposes this functionality.
     * @return suggester configuration
     */
    @GET
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    public SuggesterConfig getConfig() {
        return RuntimeEnvironment.getInstance().getConfiguration().getSuggesterConfig();
    }

    /**
     * Initializes the search data used by suggester to perform most popular completion. The passed {@code urls} are
     * decomposed into single terms and those search counts are then increased by 1.
     * @param urls list of URLs in JSON format, e.g.
     * {@code ["http://demo.opengrok.org/search?project=opengrok&q=test"]}
     */
    @POST
    @Path("/init/queries")
    @Localhost
    @Consumes(MediaType.APPLICATION_JSON)
    public void addSearchCountsPlain(final List<String> urls) {
        for (String urlStr : urls) {
            try {
                URL url = new URL(urlStr);
                Map<String, List<String>> params = Util.getQueryParams(url);

                List<String> projects = params.get("project");

                for (String field : QueryBuilder.searchFields) {

                    List<String> fieldQueryText = params.get(field);
                    if (fieldQueryText == null || fieldQueryText.isEmpty()) {
                        continue;
                    }
                    if (fieldQueryText.size() > 2) {
                        logger.log(Level.WARNING, "Bad format, ignorign");
                        continue;
                    }
                    String value = fieldQueryText.get(0);

                    Query q = null;
                    try {
                        q = getQuery(field, value);
                    } catch (ParseException e) {
                        logger.log(Level.FINE, "Bad request", e);
                    }

                    if (q != null) {
                        suggester.onSearch(projects, q);
                    }
                }
            } catch (MalformedURLException e) {
                logger.log(Level.WARNING, "Could not add search counts for " + urlStr, e);
            }
        }
    }

    private Query getQuery(final String field, final String value) throws ParseException {
        QueryBuilder builder = new QueryBuilder();

        switch (field) {
            case "q":
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
                return null;
        }

        return builder.build();
    }

    /**
     * Initializes the search data used by suggester to perform most popular completion.
     * @param termIncrements data by which to initialize the search data
     */
    @POST
    @Path("/init/raw")
    @Localhost
    @Consumes(MediaType.APPLICATION_JSON)
    public void addSearchCountsJson(@Valid final List<TermIncrementData> termIncrements) {
        for (TermIncrementData termIncrement : termIncrements) {
            suggester.increaseSearchCount(termIncrement.project,
                    new Term(termIncrement.field, termIncrement.token), termIncrement.increment);
        }
    }

    private static class Result {

        private long time;

        private List<LookupResultItem> suggestions;

        private String identifier;

        private String queryText;

        Result(
                final List<LookupResultItem> suggestions,
                final String identifier,
                final String queryText,
                final long time
        ) {
            this.suggestions = suggestions;
            this.identifier = identifier;
            this.queryText = queryText;
            this.time = time;
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
    }

    private static class TermIncrementData {

        @NotEmpty(message = "Project cannot be empty")
        private String project;

        @NotEmpty(message = "Field cannot be empty")
        private String field;

        @NotEmpty(message = "Token cannot be empty")
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
