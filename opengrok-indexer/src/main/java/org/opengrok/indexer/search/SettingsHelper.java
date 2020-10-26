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
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search;

import org.apache.lucene.index.IndexReader;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.index.IndexAnalysisSettings3;
import org.opengrok.indexer.index.IndexAnalysisSettingsAccessor;
import org.opengrok.indexer.index.IndexedSymlink;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Represents a helper class for accessing settings.
 * @author Jens Elkner
 */
public class SettingsHelper {

    private final IndexReader reader;

    /**
     * Key is Project name or empty string for null Project.
     */
    private Map<String, IndexAnalysisSettings3> mappedAnalysisSettings;

    /**
     * Key is Project name or empty string for null Project. Map is ordered by
     * canonical length (ASC) and then canonical value (ASC).
     */
    private Map<String, Map<String, IndexedSymlink>> mappedIndexedSymlinks;

    public SettingsHelper(IndexReader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("reader is null");
        }
        this.reader = reader;
    }

    /**
     * Gets any mapped symlinks (after having called {@link #getSettings(String)}).
     * @return either a defined map or {@code null}
     */
    public Map<String, IndexedSymlink> getSymlinks(String projectName) throws IOException {
        getSettings(projectName);
        String projectKey = projectName != null ? projectName : "";
        Map<String, IndexedSymlink> indexSymlinks = mappedIndexedSymlinks.get(projectKey);
        if (indexSymlinks != null) {
            return Collections.unmodifiableMap(indexSymlinks);
        }
        return null;
    }

    /**
     * Gets the persisted tabSize via {@link #getSettings(String)} if
     * available or returns the {@code proj} tabSize if available -- or zero.
     * @param proj a defined instance or {@code null} if no project is active
     * @return tabSize
     * @throws IOException if an I/O error occurs querying the initialized
     * reader
     */
    public int getTabSize(Project proj) throws IOException {
        String projectName = proj != null ? proj.getName() : null;
        IndexAnalysisSettings3 settings = getSettings(projectName);
        int tabSize;
        if (settings != null && settings.getTabSize() != null) {
            tabSize = settings.getTabSize();
        } else {
            tabSize = proj != null ? proj.getTabSize() : 0;
        }
        return tabSize;
    }

    /**
     * Gets the settings for a specified project.
     * @param projectName a defined instance or {@code null} if no project is
     * active (or empty string to mean the same thing)
     * @return a defined instance or {@code null} if none is found
     * @throws IOException if an I/O error occurs querying the initialized reader
     */
    public IndexAnalysisSettings3 getSettings(String projectName) throws IOException {
        if (mappedAnalysisSettings == null) {
            IndexAnalysisSettingsAccessor dao = new IndexAnalysisSettingsAccessor();
            IndexAnalysisSettings3[] setts = dao.read(reader, Short.MAX_VALUE);
            map(setts);
        }

        String projectKey = projectName != null ? projectName : "";
        return mappedAnalysisSettings.get(projectKey);
    }

    private void map(IndexAnalysisSettings3[] setts) {

        Map<String, IndexAnalysisSettings3> settingsMap = new HashMap<>();
        Map<String, Map<String, IndexedSymlink>> symlinksMap = new HashMap<>();

        for (IndexAnalysisSettings3 settings : setts) {
            String projectName = settings.getProjectName();
            String projectKey = projectName != null ? projectName : "";
            settingsMap.put(projectKey, settings);
            symlinksMap.put(projectKey, mapSymlinks(settings));
        }
        mappedAnalysisSettings = settingsMap;
        mappedIndexedSymlinks = symlinksMap;
    }

    private Map<String, IndexedSymlink> mapSymlinks(IndexAnalysisSettings3 settings) {

        Map<String, IndexedSymlink> res = new TreeMap<>(
                Comparator.comparingInt(String::length).thenComparing(o -> o));
        for (IndexedSymlink entry : settings.getIndexedSymlinks().values()) {
            res.put(entry.getCanonical(), entry);
        }
        return res;
    }
}
