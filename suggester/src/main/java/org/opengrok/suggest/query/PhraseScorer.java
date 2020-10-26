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
package org.opengrok.suggest.query;

import org.opengrok.suggest.query.data.IntsHolder;

/**
 * Interface for {@link org.apache.lucene.search.Scorer} of {@link org.apache.lucene.search.PhraseQuery} which allows
 * to query the positions where the next term in the query should be.
 */
public interface PhraseScorer {

    /**
     * Returns the possible positions of the term to fit the {@link SuggesterPhraseQuery}.
     * @param docId document id
     * @return positions where the next term might occur
     */
    IntsHolder getPositions(int docId);

}
