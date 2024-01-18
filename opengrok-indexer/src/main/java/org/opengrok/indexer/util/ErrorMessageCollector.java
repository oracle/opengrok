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
 * Copyright (c) 2023, Oracle and/or its affiliates.
 * Portions Copyright (c) 2023, Gino Augustine <gino.augustine@oracle.com>.
 */
package org.opengrok.indexer.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Custom collector to collect error messages for list of projects and join them with comma.
 *
 *
 * @author Gino Augustine
 */
public class ErrorMessageCollector implements Collector<String, StringJoiner, Optional<String>> {

    private final String prefix;
    private final String emptyString;
    private final Boolean returnNullWhenEmpty;

    /**
     * Creates a collector with given prefix and
     * returns optional empty for empty collection .
     * @param prefix prefix before the joined string
     */
    public ErrorMessageCollector(@NotNull String prefix) {
        this(prefix, null);
    }

    /**
     * Creates a string joiner with given prefix and empty string.
     * @param prefix prefix before the joined string
     * @param emptyString empty string to display if collection is empty
     */
    public ErrorMessageCollector(@NotNull String prefix, @Nullable String emptyString) {
        this.prefix = prefix;
        this.emptyString = Objects.isNull(emptyString) ? "" : emptyString;
        this.returnNullWhenEmpty = Objects.isNull(emptyString);

    }
    /**
     * A function that creates and returns a new mutable result container.
     *
     * @return a function which returns a new, mutable result container
     */
    @Override
    public Supplier<StringJoiner> supplier() {
        return () -> {
            var joiner = new StringJoiner(", ", emptyString + prefix, "");
            joiner.setEmptyValue(emptyString);
            return joiner;
        };
    }

    /**
     * A function that folds a value into a mutable result container.
     *
     * @return a function which folds a value into a mutable result container
     */
    @Override
    public BiConsumer<StringJoiner, String> accumulator() {
        return StringJoiner::add;
    }

    /**
     * A function that accepts two partial results and merges them.  The
     * combiner function may fold state from one argument into the other and
     * return that, or may return a new result container.
     *
     * @return a function which combines two partial results into a combined
     * result
     */
    @Override
    public BinaryOperator<StringJoiner> combiner() {
        return StringJoiner::merge;
    }

    /**
     * Perform the final transformation from the intermediate accumulation type
     * {@code A} to the final result type {@code R}.
     *
     * <p>If the characteristic {@code IDENTITY_FINISH} is
     * set, this function may be presumed to be an identity transform with an
     * unchecked cast from {@code A} to {@code R}.
     *
     * @return a function which transforms the intermediate result to the final
     * result
     */
    @Override
    public Function<StringJoiner, Optional<String>> finisher() {
        return stringJoiner ->
            Optional.of(stringJoiner.toString())
                    .filter(msg -> !(msg.isEmpty() && returnNullWhenEmpty));

    }

    /**
     * Returns a {@code Set} of {@code Collector.Characteristics} indicating
     * the characteristics of this Collector.  This set should be immutable.
     *
     * @return an immutable set of collector characteristics
     */
    @Override
    public Set<Characteristics> characteristics() {
        return Collections.emptySet();
    }
}
