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
 */
package org.opengrok.web.api.v1.suggester.parser;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.opengrok.suggest.query.SuggesterQuery;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.web.api.v1.suggester.model.SuggesterData;
import org.opengrok.web.api.v1.suggester.model.SuggesterQueryData;
import org.opengrok.web.api.v1.suggester.query.SuggesterQueryBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parser for the raw {@link SuggesterQueryData}.
 */
public class SuggesterQueryDataParser {

    private static final int IDENTIFIER_LENGTH = 5;

    private static final Logger logger = LoggerFactory.getLogger(SuggesterQueryDataParser.class);

    private SuggesterQueryDataParser() {
    }

    /**
     * Parses the {@link SuggesterQueryData}.
     * @param data data to parse
     * @return parsed data for the suggester use
     * @throws ParseException if could not parse the search data into a valid {@link Query}
     */
    public static SuggesterData parse(final SuggesterQueryData data) throws ParseException {
        Map<String, String> fieldQueries = getFieldQueries(data);

        ProcessedQueryData queryData = processQuery(fieldQueries.get(data.getField()), data.getCaretPosition());

        fieldQueries.put(data.getField(), queryData.query);

        SuggesterQueryBuilder builder = new SuggesterQueryBuilder(data.getField(), queryData.identifier);

        builder.setFreetext(fieldQueries.get(QueryBuilder.FULL))
                .setDefs(fieldQueries.get(QueryBuilder.DEFS))
                .setRefs(fieldQueries.get(QueryBuilder.REFS))
                .setPath(fieldQueries.get(QueryBuilder.PATH))
                .setHist(fieldQueries.get(QueryBuilder.HIST))
                .setType(fieldQueries.get(QueryBuilder.TYPE));

        Query query;
        try {
            query = builder.build();
        } catch (ParseException e) {
            // remove identifier from the message
            // the position might be still wrong if the parse error was at the end of the identifier
            throw new ParseException(e.getMessage().replaceAll(queryData.identifier, ""));
        }

        SuggesterQuery suggesterQuery = builder.getSuggesterQuery();

        // builder can return the suggester query if it was simple query, we ignore it in that case
        if (query.equals(suggesterQuery)) {
            query = null;
        }

        return new SuggesterData(suggesterQuery, data.getProjects(), query, builder.getQueryTextWithPlaceholder(),
                builder.getIdentifier());
    }

    private static ProcessedQueryData processQuery(final String text, final int caretPosition) {
        if (text == null) {
            throw new IllegalArgumentException("Cannot process null text");
        }
        if (caretPosition > text.length()) {
            throw new IllegalArgumentException("Caret position has greater value than text length");
        }

        logger.log(Level.FINEST, "Processing suggester query: {0} at {1}", new Object[] {text, caretPosition});

        String randomIdentifier = RandomStringUtils.
                randomAlphabetic(IDENTIFIER_LENGTH).toLowerCase(); // OK no ROOT
        while (text.contains(randomIdentifier)) {
            randomIdentifier = RandomStringUtils.
                    randomAlphabetic(IDENTIFIER_LENGTH).toLowerCase(); // OK no ROOT
        }

        String newText = new StringBuilder(text).insert(caretPosition, randomIdentifier).toString();

        return new ProcessedQueryData(randomIdentifier, newText);
    }

    private static Map<String, String> getFieldQueries(final SuggesterQueryData data) {
        Map<String, String> map = new HashMap<>();

        map.put(QueryBuilder.FULL, data.getFull());
        map.put(QueryBuilder.DEFS, data.getDefs());
        map.put(QueryBuilder.REFS, data.getRefs());
        map.put(QueryBuilder.PATH, data.getPath());
        map.put(QueryBuilder.HIST, data.getHist());
        map.put(QueryBuilder.TYPE, data.getType());

        return map;
    }

    private static class ProcessedQueryData {

        final String identifier;
        final String query;

        ProcessedQueryData(final String identifier, final String query) {
            this.identifier = identifier;
            this.query = query;
        }
    }

}
