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
import org.opengrok.suggest.LookupResultItem;
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
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/")
public final class SuggesterController {

    private static final Logger logger = LoggerFactory.getLogger(SuggesterController.class);

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Inject
    private SuggesterService suggester;

    @GET
    @Authorized
    @Produces(MediaType.APPLICATION_JSON)
    public Result getSuggestions(@Valid @BeanParam final SuggesterQueryData data) throws ParseException {
        Instant start = Instant.now();

        SuggesterData suggesterData = SuggesterQueryDataParser.parse(data);

        SuggesterConfig config = env.getConfiguration().getSuggester();

        modifyDataBasedOnConfiguration(suggesterData, config);

        if (!satisfiesConfiguration(suggesterData, config)) {
            logger.log(Level.FINER, "Suggester request with data {0} does not satisfy configuration settings", data);
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

        if (!config.isAllowComplexQueries() && data.getQuery() != null) {
            return false;
        }

        return true;
    }

    @GET
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    public SuggesterConfig getConfig() {
        return RuntimeEnvironment.getInstance().getConfiguration().getSuggester();
    }

    @POST
    @Path("/add")
    @Localhost
    @Consumes(MediaType.TEXT_PLAIN)
    public void addSearchCountsPlain(final String body) {
        if (body == null) {
            return;
        }
        for (String urlStr : body.split("\\r?\\n")) {
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
                return null;
        }

        return builder.build();
    }

    @POST
    @Path("/add")
    @Localhost
    @Consumes(MediaType.APPLICATION_JSON)
    public void addSearchCountsJson(final List<TermCount> termCounts) {
        for (TermCount termCount : termCounts) {
            suggester.increaseSearchCount(termCount.project, new Term(termCount.field, termCount.token), termCount.count);
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

    private static class TermCount {

        private String project;

        private String field;

        private String token;

        private int count;

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

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

}
