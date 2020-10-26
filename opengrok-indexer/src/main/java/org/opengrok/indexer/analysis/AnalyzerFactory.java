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
 * Copyright (c) 2007, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.util.Collections;
import java.util.List;

public abstract class AnalyzerFactory {
    /**
     * Cached analyzer object for the current thread (analyzer objects can be
     * expensive to allocate).
     */
    protected final ThreadLocal<AbstractAnalyzer> cachedAnalyzer;
    /**
     * List of file names on which this kind of analyzer should be used.
     */
    protected List<String> names;
    /**
     * List of file prefixes on which this kind of analyzer should be
     * used.
     */
    protected List<String> prefixes;
    /**
     * List of file extensions on which this kind of analyzer should be
     * used.
     */
    protected List<String> suffixes;
    /**
     * List of magic strings used to recognize files on which this kind of
     * analyzer should be used.
     */
    protected List<String> magics;
    /**
     * List of matchers which delegate files to different types of
     * analyzers.
     */
    protected final List<FileAnalyzerFactory.Matcher> matchers;
    /**
     * The content type for the files recognized by this kind of analyzer.
     */
    protected final String contentType;
    /**
     * The genre for files recognized by this kind of analyzer.
     */
    protected AbstractAnalyzer.Genre genre;

    public AnalyzerFactory(FileAnalyzerFactory.Matcher matcher, String contentType) {
        cachedAnalyzer = new ThreadLocal<>();
        if (matcher == null) {
            this.matchers = Collections.emptyList();
        } else {
            this.matchers = Collections.singletonList(matcher);
        }
        this.contentType = contentType;
    }

    /**
     * Get the list of file names recognized by this analyzer (names must
     * match exactly, ignoring case).
     *
     * @return list of file names
     */
    final List<String> getFileNames() {
        return names;
    }

    /**
     * Get the list of file prefixes recognized by this analyzer.
     *
     * @return list of prefixes
     */
    final List<String> getPrefixes() {
        return prefixes;
    }

    /**
     * Get the list of file extensions recognized by this analyzer.
     *
     * @return list of suffixes
     */
    final List<String> getSuffixes() {
        return suffixes;
    }

    /**
     * Get the list of magic strings recognized by this analyzer. If a file
     * starts with one of these strings, an analyzer created by this factory
     * should be used to analyze it.
     *
     * <p><b>Note:</b> Currently this assumes that the file is encoded with
     * UTF-8 unless a BOM is detected.
     *
     * @return list of magic strings
     */
    final List<String> getMagicStrings() {
        return magics;
    }

    /**
     * Get matchers that map file contents to analyzer factories
     * programmatically.
     *
     * @return list of matchers
     */
    final List<FileAnalyzerFactory.Matcher> getMatchers() {
        return matchers;
    }

    /**
     * Get the content type (MIME type) for analyzers returned by this factory.
     *
     * @return content type (could be {@code null} if it is unknown)
     */
    final String getContentType() {
        return contentType;
    }

    /**
     * The genre this analyzer factory belongs to.
     *
     * @return a genre
     */
    public final AbstractAnalyzer.Genre getGenre() {
        return genre;
    }

    /**
     * The user friendly name of this analyzer.
     *
     * @return a genre
     */
    public abstract String getName();

    public abstract AbstractAnalyzer getAnalyzer();

    public abstract void returnAnalyzer();

    protected abstract AbstractAnalyzer newAnalyzer();
}
