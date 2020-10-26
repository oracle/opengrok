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
 * Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents an API for mapping file specifications versus languages and
 * getting the ctags options representation (--langmap-&lt;LANG&gt; or
 * --map-&lt;LANG&gt;) thereof.
 */
public interface LangMap {

    /**
     * Removes all settings from this map.
     */
    void clear();

    /**
     * Adds the specified mapping of a file specification to a language. Any
     * matching exclusion via {@link #exclude(String)} is undone.
     * @param fileSpec a value starting with a period ({@code '.'}) to specify
     *                 a file extension; otherwise specifying a prefix.
     * @throws IllegalArgumentException if {@code fileSpec} is {@code null} or
     * is an extension (i.e. starting with a period) but contains any other
     * periods, as that is not ctags-compatible
     */
    void add(String fileSpec, String ctagsLang);

    /**
     * Exclude the specified mapping of a file specification to any language.
     * Any matching addition via {@link #add(String, String)} is undone.
     * @throws IllegalArgumentException if {@code fileSpec} is {@code null}
     */
    void exclude(String fileSpec);

    /**
     * Gets the transformation of the instance's mappings to ctags arguments.
     */
    List<String> getCtagsArgs();

    /**
     * Creates a new instance, merging the settings from the current instance
     * overlaying a specified {@code other}. Additions from the current instance
     * take precedence, and exclusions from the {@code other} only take effect
     * if the current instance has no matching addition.
     * @param other a defined instance
     * @return a defined instance
     */
    LangMap mergeSecondary(LangMap other);

    /**
     * Gets an unmodifiable view of the current instance.
     */
    LangMap unmodifiable();

    /**
     * Gets an unmodifiable view of the current additions.
     */
    Map<String, String> getAdditions();

    /**
     * Gets an unmodifiable view of the current exclusions.
     */
    Set<String> getExclusions();
}
