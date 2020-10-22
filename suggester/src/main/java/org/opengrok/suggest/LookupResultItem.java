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
package org.opengrok.suggest;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents one suggestion.
 */
public final class LookupResultItem implements Comparable<LookupResultItem> {

    private final String phrase;

    private final Set<String> projects = new HashSet<>();

    private long score;

    LookupResultItem(final String phrase, final String project, final long score) {
        this.phrase = phrase;
        this.projects.add(project);
        this.score = score;
    }

    public String getPhrase() {
        return phrase;
    }

    public Set<String> getProjects() {
        return Collections.unmodifiableSet(projects);
    }

    public long getScore() {
        return score;
    }

    /**
     * Combines score and projects with the other instance with the same {@code phrase}.
     * @param other instance to combine with
     */
    void combine(final LookupResultItem other) {
        if (other == null || !canBeCombinedWith(other)) {
            throw new IllegalArgumentException("Cannot combine with " + other);
        }
        projects.addAll(other.projects);
        score += other.score;
    }

    private boolean canBeCombinedWith(final LookupResultItem other) {
        return phrase.equals(other.phrase);
    }

    @Override
    public int compareTo(final LookupResultItem other) {
        return Long.compare(score, other.score);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LookupResultItem that = (LookupResultItem) o;
        return score == that.score &&
                Objects.equals(phrase, that.phrase) &&
                Objects.equals(projects, that.projects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phrase, projects, score);
    }

    @Override
    public String toString() {
        return "LookupResultItem{phrase='" + phrase + "', projects=" + projects + ", score=" + score + '}';
    }
}
