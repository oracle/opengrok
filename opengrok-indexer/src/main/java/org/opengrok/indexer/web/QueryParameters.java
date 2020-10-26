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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.web;

/**
 * Represents a container for OpenGrok web query parameter names.
 */
public class QueryParameters {
    /**
     * Parameter name to control activation of xref annotation.
     */
    public static final String ANNOTATION_PARAM = "a";

    /**
     * {@link #ANNOTATION_PARAM} concatenated with {@code "=true" }.
     */
    public static final String ANNOTATION_PARAM_EQ_TRUE = ANNOTATION_PARAM + "=true";

    /**
     * Parameter name to specify a count value.
     */
    public static final String COUNT_PARAM = "n";

    /**
     * {@link #COUNT_PARAM} concatenated with {@code "=" }.
     */
    public static final String COUNT_PARAM_EQ = COUNT_PARAM + "=";

    /**
     * Parameter name to specify an OpenGrok search of definitions.
     */
    public static final String DEFS_SEARCH_PARAM = "defs";

    /**
     * {@link #DEFS_SEARCH_PARAM} concatenated with {@code "=" }.
     */
    public static final String DEFS_SEARCH_PARAM_EQ = DEFS_SEARCH_PARAM + "=";

    /**
     * Parameter name to specify a source code degree of diffing.
     */
    public static final String DIFF_LEVEL_PARAM = "full";

    /**
     * {@link #DIFF_LEVEL_PARAM} concatenated with {@code "=" }.
     */
    public static final String DIFF_LEVEL_PARAM_EQ = DIFF_LEVEL_PARAM + "=";

    /**
     * Parameter name to specify a format setting.
     */
    public static final String FORMAT_PARAM = "format";

    /**
     * {@link #FORMAT_PARAM} concatenated with {@code "=" }.
     */
    public static final String FORMAT_PARAM_EQ = FORMAT_PARAM + "=";

    /**
     * Parameter name to specify a mediated fragment identifier.
     */
    public static final String FRAGMENT_IDENTIFIER_PARAM = "fi";

    /**
     * {@link #FRAGMENT_IDENTIFIER_PARAM} concatenated with {@code "=" }.
     */
    public static final String FRAGMENT_IDENTIFIER_PARAM_EQ = FRAGMENT_IDENTIFIER_PARAM + "=";

    /**
     * Parameter name to specify an OpenGrok full search.
     */
    public static final String FULL_SEARCH_PARAM = "full";

    /**
     * {@link #FULL_SEARCH_PARAM} concatenated with {@code "=" }.
     */
    public static final String FULL_SEARCH_PARAM_EQ = FULL_SEARCH_PARAM + "=";

    /**
     * Parameter name to specify an OpenGrok search of history.
     */
    public static final String HIST_SEARCH_PARAM = "hist";

    /**
     * {@link #HIST_SEARCH_PARAM} concatenated with {@code "=" }.
     */
    public static final String HIST_SEARCH_PARAM_EQ = HIST_SEARCH_PARAM + "=";

    /**
     * Parameter name to specify a match offset.
     */
    public static final String MATCH_OFFSET_PARAM = "mo";

    /**
     * {@link #MATCH_OFFSET_PARAM} concatenated with {@code "=" }.
     */
    public static final String MATCH_OFFSET_PARAM_EQ = MATCH_OFFSET_PARAM + "=";

    /**
     * Parameter name to specify a value indicating if redirection should be
     * short-circuited when state or query result would have an indicated
     * otherwise.
     */
    public static final String NO_REDIRECT_PARAM = "xrd";

    /**
     * Parameter name to specify a count of projects selected by the user
     * through browser interaction.
     */
    public static final String NUM_SELECTED_PARAM = "nn";

    /**
     * Parameter name to specify an OpenGrok search of paths.
     */
    public static final String PATH_SEARCH_PARAM = "path";

    /**
     * {@link #PATH_SEARCH_PARAM} concatenated with {@code "=" }.
     */
    public static final String PATH_SEARCH_PARAM_EQ = PATH_SEARCH_PARAM + "=";

    /**
     * Parameter name to specify an OpenGrok project search.
     */
    public static final String PROJECT_SEARCH_PARAM = "project";

    /**
     * {@link #PROJECT_SEARCH_PARAM} concatenated with {@code "=" }.
     */
    public static final String PROJECT_SEARCH_PARAM_EQ = PROJECT_SEARCH_PARAM + "=";

    /**
     * Parameter name to specify an OpenGrok search of references of symbols.
     */
    public static final String REFS_SEARCH_PARAM = "refs";

    /**
     * {@link #REFS_SEARCH_PARAM} concatenated with {@code "=" }.
     */
    public static final String REFS_SEARCH_PARAM_EQ = REFS_SEARCH_PARAM + "=";

    /**
     * Parameter name to specify a source repository revision ID.
     */
    public static final String REVISION_PARAM = "r";

    /**
     * {@link #REVISION_PARAM} concatenated with {@code "=" }.
     */
    public static final String REVISION_PARAM_EQ = REVISION_PARAM + "=";

    /**
     * Parameter name to specify a first source repository revision ID.
     */
    public static final String REVISION_1_PARAM = "r1";

    /**
     * {@link #REVISION_1_PARAM} concatenated with {@code "=" }.
     */
    public static final String REVISION_1_PARAM_EQ = REVISION_1_PARAM + "=";

    /**
     * Parameter name to specify a second source repository revision ID.
     */
    public static final String REVISION_2_PARAM = "r2";

    /**
     * {@link #REVISION_2_PARAM} concatenated with {@code "=" }.
     */
    public static final String REVISION_2_PARAM_EQ = REVISION_2_PARAM + "=";

    /**
     * Parameter name to specify a sort setting.
     */
    public static final String SORT_PARAM = "sort";

    /**
     * {@link #SORT_PARAM} concatenated with {@code "=" }.
     */
    public static final String SORT_PARAM_EQ = SORT_PARAM + "=";

    /**
     * Parameter name to specify a starting value.
     */
    public static final String START_PARAM = "start";

    /**
     * {@link #START_PARAM} concatenated with {@code "=" }.
     */
    public static final String START_PARAM_EQ = START_PARAM + "=";

    /**
     * Parameter name to specify an OpenGrok search of file type.
     */
    public static final String TYPE_SEARCH_PARAM = "type";

    /**
     * {@link #TYPE_SEARCH_PARAM} concatenated with {@code "=" }.
     */
    public static final String TYPE_SEARCH_PARAM_EQ = TYPE_SEARCH_PARAM + "=";

    /**
     * Parameter name to specify window hash for utils-*.js.
     */
    public static final String WINDOW_HASH_PARAM = "h";

    /**
     * {@link #WINDOW_HASH_PARAM} concatenated with {@code "=" }.
     */
    public static final String WINDOW_HASH_PARAM_EQ = WINDOW_HASH_PARAM + "=";

    /* private to enforce static */
    private QueryParameters() {
    }
}
