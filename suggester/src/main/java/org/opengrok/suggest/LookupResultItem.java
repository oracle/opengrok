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
package org.opengrok.suggest;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class LookupResultItem implements Comparable<LookupResultItem> {

    private final String phrase;

    private final Set<String> suggesters = new HashSet<>();

    private long weight;

    LookupResultItem(final String phrase, final String suggester, final long weight) {
        this.phrase = phrase;
        this.suggesters.add(suggester);
        this.weight = weight;
    }

    public String getPhrase() {
        return phrase;
    }

    public Set<String> getSuggesters() {
        return Collections.unmodifiableSet(suggesters);
    }

    public long getWeight() {
        return weight;
    }

    void combine(final LookupResultItem other) {
        if (!canBeCombinedWith(other)) {
            throw new IllegalArgumentException("Cannot combine with " + other);
        }
        suggesters.addAll(other.suggesters);
        weight += other.weight;
    }

    private boolean canBeCombinedWith(final LookupResultItem other) {
        return phrase.equals(other.phrase);
    }

    @Override
    public int compareTo(final LookupResultItem other) {
        return Long.compare(weight, other.weight);
    }

    @Override
    public String toString() {
        return "LookupResultItem{phrase='" + phrase + "', suggesters=" + suggesters + ", weight=" + weight + '}';
    }
}
