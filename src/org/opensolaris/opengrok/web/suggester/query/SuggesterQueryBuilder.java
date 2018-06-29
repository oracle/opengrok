package org.opensolaris.opengrok.web.suggester.query;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.opengrok.suggest.query.SuggesterQuery;
import org.opensolaris.opengrok.search.CustomQueryParser;
import org.opensolaris.opengrok.search.QueryBuilder;

public class SuggesterQueryBuilder extends QueryBuilder {

    private final String suggestField;
    private final String identifier;

    private SuggesterQueryParser suggesterQueryParser;

    public SuggesterQueryBuilder(final String suggestField, final String identifier) {
        this.suggestField = suggestField;
        this.identifier = identifier;
    }

    public SuggesterQuery getSuggesterQuery() {
        return suggesterQueryParser.getSuggesterQuery();
    }

    public String getIdentifier() {
        return suggesterQueryParser.getIdentifier();
    }

    public String getQueryTextWithPlaceholder() {
        return suggesterQueryParser.getQueryTextWithPlaceholder();
    }

    /** {@inheritDoc} */
    @Override
    protected Query buildQuery(final String field, final String queryText)
            throws ParseException {

        if (field.equals(suggestField)) {
            suggesterQueryParser = new SuggesterQueryParser(field, identifier);
            return suggesterQueryParser.parse(queryText);
        } else {
            return new CustomQueryParser(field).parse(queryText);
        }
    }

}
