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
package org.opengrok.web.api.v1.suggester.provider.service;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.opengrok.suggest.Suggester.Suggestions;
import org.opengrok.suggest.query.SuggesterQuery;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

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
    Suggestions getSuggestions(Collection<String> projects, SuggesterQuery suggesterQuery, Query query);

    /**
     * Refreshes the suggester based on the new configuration.
     */
    void refresh();

    /**
     * Rebuild suggester data structures. This is a subset of what {@code refresh} does.
     */
    void rebuild();

    /**
     * Rebuilds suggester data structures for given project. This is a subset of what {@code refresh} does.
     * @param project project name
     */
    void rebuild(String project);

    /**
     * Wait for rebuild. For testing.
     * @param timeout timeout to wait for
     * @param unit timeout unit
     */
    void waitForRebuild(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Wait for the initialization. For testing.
     * @param timeout timeout to wait for
     * @param unit timeout unit
     */
    void waitForInit(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Deletes all suggester data for the {@code project}.
     * @param project name of the project to delete
     */
    void delete(String project);

    /**
     * Increments most popular completion data according to the passed parameters.
     * @param projects projects that were searched
     * @param q search query
     */
    void onSearch(Iterable<String> projects, Query q);

    /**
     * Increments most popular completion data for the specified {@code term} by {@code value}.
     * @param project project to update
     * @param term term to update
     * @param value value by which to change the data, represents how many times was the {@code term} searched
     * @return false if update failed, otherwise true
     */
    boolean increaseSearchCount(String project, Term term, int value);

    /**
     * Increments most popular completion data for the specified {@code term} by {@code value}.
     * @param project project to update
     * @param term term to update
     * @param value value by which to change the data, represents how many times was the {@code term} searched
     * @param waitForLock wait for lock
     * @return false if update failed, otherwise true
     */
    boolean increaseSearchCount(String project, Term term, int value, boolean waitForLock);

    /**
     * Returns the searched terms sorted according to their popularity.
     * @param project project for which to return the data
     * @param field field for which to return the data
     * @param page which page of data to retrieve
     * @param pageSize number of results to return
     * @return list of terms with their popularity
     */
    List<Entry<BytesRef, Integer>> getPopularityData(String project, String field, int page, int pageSize);

    /**
     * Closes the underlying service explicitly.
     */
    void close();
}
