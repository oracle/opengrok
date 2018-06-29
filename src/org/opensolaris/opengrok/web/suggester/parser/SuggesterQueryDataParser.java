package org.opensolaris.opengrok.web.suggester.parser;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.opengrok.suggest.query.SuggesterQuery;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.web.suggester.model.SuggesterData;
import org.opensolaris.opengrok.web.suggester.model.SuggesterQueryData;
import org.opensolaris.opengrok.web.suggester.query.SuggesterQueryBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SuggesterQueryDataParser {

    private static final int IDENTIFIER_LENGTH = 5;

    private static final Logger logger = Logger.getLogger(SuggesterQueryDataParser.class.getName());

    private SuggesterQueryDataParser() {

    }

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

        Query query = builder.build();

        SuggesterQuery suggesterQuery = builder.getSuggesterQuery();

        // builder can return the suggester query if it was simple query, we ignore the query in that case
        if (query.equals(suggesterQuery)) {
            query = null;
        }

        return new SuggesterData(suggesterQuery, data.getProjects(), query, builder.getQueryTextWithPlaceholder(), builder.getIdentifier());
    }

    private static ProcessedQueryData processQuery(final String text, final int caretPosition) {
        logger.log(Level.FINER, "Processing: {0} at {1}", new Object[] {text, caretPosition});

        String randomIdentifier = RandomStringUtils.randomAlphabetic(IDENTIFIER_LENGTH).toLowerCase();
        while (text.contains(randomIdentifier)) {
            randomIdentifier = RandomStringUtils.randomAlphabetic(IDENTIFIER_LENGTH).toLowerCase();
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
