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
package org.opengrok.web.api.v1.suggester.model;

import org.apache.lucene.search.Query;
import org.opengrok.suggest.query.SuggesterQuery;

import java.util.List;

/**
 * Represents processed {@link SuggesterQueryData}.
 */
public final class SuggesterData {

    private final SuggesterQuery suggesterQuery;

    private final List<String> projects;

    private final Query query;

    private final String suggesterQueryFieldText;

    private final String identifier;

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
