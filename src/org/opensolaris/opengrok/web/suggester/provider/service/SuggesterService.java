package org.opensolaris.opengrok.web.suggester.provider.service;

import org.apache.lucene.search.Query;
import org.opengrok.suggest.LookupResultItem;
import org.opengrok.suggest.query.SuggesterQuery;

import java.util.List;

public interface SuggesterService {

    /**
     * Returns suggestions based on the provided parameters.
     * @param projects projects for which to provide suggestions
     * @param suggesterQuery query for which suggestions should be provided,
     *                       e.g. {@link org.apache.lucene.search.PrefixQuery}
     * @param query query that restricts {@code suggesterQuery}. Can be null. If specified then the suggester query is
     *              considered to be complex because it takes more resources to find the proper suggestions.
     * @return suggestions to be displayed
     */
    List<LookupResultItem> getSuggestions(List<String> projects, SuggesterQuery suggesterQuery, Query query);

    void refresh(String project);

    void delete(String project);

    void onSearch(Iterable<String> projects, Query q);

    void close();

}
