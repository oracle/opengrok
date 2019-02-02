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
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A class wrapping information about used analyzers.
 */
public class AnalyzersInfo {

    /**
     * Descending string length comparator for magics
     */
    private static final Comparator<String> DESCENDING_LENGTH_COMPARATOR =
            Comparator.comparingInt(String::length).thenComparing(String::toString);

    /**
     * Modified when
     * {@link AnalyzerFramework#addExtension(String, AnalyzerFactory)}
     * or
     * {@link AnalyzerFramework#addPrefix(String, AnalyzerFactory)}
     * are called to augment the value in {@link AnalyzerGuru#getVersionNo()}.
     */
    public final SortedSet<String> customizations;

    /**
     * Map from file names to analyzer factories.
     */
    public final Map<String, AnalyzerFactory> fileNames;

    /**
     * Map from file extensions to analyzer factories.
     */
    public final Map<String, AnalyzerFactory> extensions;

    /**
     * Map from file prefixes to analyzer factories.
     */
    public final Map<String, AnalyzerFactory> prefixes;

    /**
     * Map from magic strings to analyzer factories.
     */
    public final SortedMap<String, AnalyzerFactory> magics;

    /**
     * List of matcher objects which can be used to determine which analyzer
     * factory to use.
     */
    public final List<FileAnalyzerFactory.Matcher> matchers;

    /**
     * List of all registered {@code FileAnalyzerFactory} instances.
     */
    public final List<AnalyzerFactory> factories;

    /**
     * A map of {@link FileAnalyzer#getFileTypeName()} to human readable analyzer name.
     */
    public final Map<String, String> fileTypeDescriptions;

    /**
     * Maps from {@link FileAnalyzer#getFileTypeName()} to
     * {@link FileAnalyzerFactory}
     */
    public final Map<String, AnalyzerFactory> filetypeFactories;

    /**
     * Maps from {@link FileAnalyzer#getFileTypeName()} to
     * {@link FileAnalyzer#getVersionNo()}
     */
    public final Map<String, Long> analyzerVersions;


    /**
     * Construct an empty analyzers info.
     */
    public AnalyzersInfo() {
        this(
                new TreeSet<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new TreeMap<>(DESCENDING_LENGTH_COMPARATOR),
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        );
    }

    /**
     * Construct the analyzers info with given collections.
     *
     * @param customizations       the customization keys set
     * @param fileNames            map of filenames to analyzers factories
     * @param extensions             map of extensions to analyzers factories
     * @param prefixes             map of prefixes to analyzers factories
     * @param magics               map of magics to analyzers factories
     * @param matchers             a list of matchers for analyzers factories
     * @param factories            a list of analyzers factories
     * @param fileTypeDescriptions map of analyzer names to analyzer descriptions
     * @param filetypeFactories    map of file type to analyzers factories
     * @param analyzerVersions     map of analyzer names to analyzer versions
     */
    private AnalyzersInfo(
            SortedSet<String> customizations,
            Map<String, AnalyzerFactory> fileNames,
            Map<String, AnalyzerFactory> extensions,
            Map<String, AnalyzerFactory> prefixes,
            SortedMap<String, AnalyzerFactory> magics,
            List<FileAnalyzerFactory.Matcher> matchers,
            List<AnalyzerFactory> factories,
            Map<String, String> fileTypeDescriptions,
            Map<String, AnalyzerFactory> filetypeFactories,
            Map<String, Long> analyzerVersions
    ) {
        this.customizations = customizations;
        this.fileNames = fileNames;
        this.extensions = extensions;
        this.prefixes = prefixes;
        this.magics = magics;
        this.matchers = matchers;
        this.factories = factories;
        this.fileTypeDescriptions = fileTypeDescriptions;
        this.filetypeFactories = filetypeFactories;
        this.analyzerVersions = analyzerVersions;
    }

    /**
     * Make the object unmodifiable.
     *
     * @return a new object reference with all properties wrapped as unmodifiable collections
     */
    public AnalyzersInfo freeze() {
        return new AnalyzersInfo(
                Collections.unmodifiableSortedSet(this.customizations),
                Collections.unmodifiableMap(this.fileNames),
                Collections.unmodifiableMap(this.extensions),
                Collections.unmodifiableMap(this.prefixes),
                Collections.unmodifiableSortedMap(this.magics),
                Collections.unmodifiableList(this.matchers),
                Collections.unmodifiableList(this.factories),
                Collections.unmodifiableMap(this.fileTypeDescriptions),
                Collections.unmodifiableMap(this.filetypeFactories),
                Collections.unmodifiableMap(this.analyzerVersions)
        );
    }
}
