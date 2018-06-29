package org.opensolaris.opengrok.web.suggester.model;

import org.apache.lucene.search.Query;
import org.opengrok.suggest.query.SuggesterQuery;

import java.util.List;

public final class SuggesterData {

    private final SuggesterQuery suggesterQuery;

    private final List<String> projects;

    private final Query query;

    private String suggesterQueryFieldText;

    private String identifier;

    public SuggesterData(
            final SuggesterQuery suggesterQuery,
            final List<String> projects,
            final Query query,
            final String suggesterQueryFieldText,
            final String identifier
    ) {
        this.suggesterQuery = suggesterQuery;
        this.projects = projects;
        this.query = query;
        this.suggesterQueryFieldText = suggesterQueryFieldText;
        this.identifier = identifier;
    }

    public SuggesterQuery getSuggesterQuery() {
        return suggesterQuery;
    }

    public List<String> getProjects() {
        return projects;
    }

    public Query getQuery() {
        return query;
    }

    public String getSuggesterQueryFieldText() {
        return suggesterQueryFieldText;
    }

    public String getIdentifier() {
        return identifier;
    }

}
