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

import org.apache.lucene.queryparser.classic.ParseException;
import org.opengrok.suggest.LookupResultItem;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.SuggesterConfig;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.web.suggester.model.SuggesterData;
import org.opensolaris.opengrok.web.suggester.model.SuggesterQueryData;
import org.opensolaris.opengrok.web.suggester.parser.SuggesterQueryDataParser;
import org.opensolaris.opengrok.web.suggester.provider.service.SuggesterService;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/")
public final class SuggesterController {

    private final Logger logger = LoggerFactory.getLogger(SuggesterController.class);

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Inject
    private SuggesterService suggester;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Result getSuggestions(@Valid @BeanParam final SuggesterQueryData data) throws ParseException {
        try {
            Instant start = Instant.now();

            SuggesterData suggesterData = SuggesterQueryDataParser.parse(data);

            Configuration mainConfig = env.getConfiguration();
            if (mainConfig == null) {
                throw new IllegalStateException("No configuration specified");
            }

            SuggesterConfig config = mainConfig.getSuggester();

            modifyDataBasedOnConfiguration(suggesterData, config);

            if (!satisfiesConfiguration(suggesterData, config)) {
                logger.log(Level.FINER, "Suggester request with data {0} does not satisfy configuration settings", data);
            }

            List<LookupResultItem> suggestedItems = suggester.getSuggestions(
                    suggesterData.getProjects(), suggesterData.getSuggesterQuery(), suggesterData.getQuery());

            Instant end = Instant.now();

            long timeInMs = Duration.between(start, end).toMillis();

            return new Result(suggestedItems, suggesterData.getIdentifier(), suggesterData.getSuggesterQueryFieldText(), timeInMs);
        } catch (Exception e) {
            throw new ParseException();
        }
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

    private static class Result {

        private long time;

        private List<LookupResultItem> suggestions;

        private String identifier;

        private String queryText;

        Result(List<LookupResultItem> suggestions, String identifier, String queryText, long time) {
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

}
