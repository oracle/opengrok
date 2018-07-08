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
package org.opensolaris.opengrok.web.suggester.provider.service;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.opengrok.suggest.LookupResultItem;
import org.opengrok.suggest.query.SuggesterQuery;
import org.opensolaris.opengrok.configuration.Configuration;

import java.util.Collection;
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
    List<LookupResultItem> getSuggestions(Collection<String> projects, SuggesterQuery suggesterQuery, Query query);

    void refresh(Configuration configuration);

    void refresh(String project);

    void delete(String project);

    void onSearch(Iterable<String> projects, Query q);

    void increaseSearchCount(String project, Term term, int value);

    void close();

}
