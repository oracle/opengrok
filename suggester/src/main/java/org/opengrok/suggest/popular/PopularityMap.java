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
package org.opengrok.suggest.popular;

import org.apache.lucene.util.BytesRef;

import java.util.List;
import java.util.Map.Entry;

/**
 * Abstraction for the map to store the data for most popular completion.
 */
public interface PopularityMap extends PopularityCounter, AutoCloseable {

    /**
     * Increment data for the {@code key} by {@code value}.
     * @param key term to increment data for
     * @param value positive value by which to increment the data
     */
    void increment(BytesRef key, int value);

    /**
     * Returns the popularity data sorted according to their value.
     * @param page which page of data to retrieve
     * @param pageSize number of results to return
     * @return popularity data sorted according to their value
     */
    List<Entry<BytesRef, Integer>> getPopularityData(int page, int pageSize);

    /** {@inheritDoc} */
    @Override
    void close();

}
