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
 * Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.IOException;
import java.io.InvalidClassException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.AnalyzerGuru;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Represents a test container for {@link IndexAnalysisSettingsUpgrader}.
 */
public class IndexAnalysisSettingsUpgraderTest {

    private static final String PROJECT_NAME = "foo-1-2-3";
    private static final long ANALYZER_GURU_VERSION = 3;
    private static final int TABSIZE = 17;
    private static final Map<String, IndexedSymlink> TEST_MAPPED_SYMLINKS;

    static {
        TEST_MAPPED_SYMLINKS = new HashMap<>();
        IndexedSymlink entry = new IndexedSymlink("/foo", "/private/foo", false);
        TEST_MAPPED_SYMLINKS.put(entry.getCanonical(), entry);
        entry = new IndexedSymlink("/foo/bar", "/private/foo/bar", true);
        TEST_MAPPED_SYMLINKS.put(entry.getCanonical(), entry);
    }

    @Test
    public void shouldHandleLatest() throws IOException,
            ClassNotFoundException {
        IndexAnalysisSettings3 obj = new IndexAnalysisSettings3();
        obj.setAnalyzerGuruVersion(ANALYZER_GURU_VERSION);
        Map<String, Long> actAnalyzersVersionNos = AnalyzerGuru.getAnalyzersVersionNos();
        obj.setAnalyzersVersions(actAnalyzersVersionNos);
        obj.setProjectName(PROJECT_NAME);
        obj.setTabSize(TABSIZE);
        obj.setIndexedSymlinks(TEST_MAPPED_SYMLINKS);
        byte[] bin = obj.serialize();

        IndexAnalysisSettingsUpgrader upgrader = new IndexAnalysisSettingsUpgrader();
        IndexAnalysisSettings3 vLatest = upgrader.upgrade(bin, 3);
        assertNotNull(vLatest, "should get non-null from upgrader");
        assertEquals(PROJECT_NAME, vLatest.getProjectName(), "should have same projectName");
        assertEquals(TABSIZE, (int) vLatest.getTabSize(), "should have same tabSize");
        assertEquals(ANALYZER_GURU_VERSION, (long) vLatest.getAnalyzerGuruVersion(),
                "should have same analyzerGuruVersion");
        assertEquals(vLatest.getAnalyzersVersions().size(), actAnalyzersVersionNos.size(),
                "should have expected analyzer versions");

        Object[] expectedVersionKeys = actAnalyzersVersionNos.keySet().stream().sorted().toArray();
        assertArrayEquals(expectedVersionKeys,
                vLatest.getAnalyzersVersions().keySet().stream().sorted().toArray(),
                "analyzer versions keysets should be equal");
        assertArrayEquals(getMapValues(actAnalyzersVersionNos, expectedVersionKeys),
                getMapValues(vLatest.getAnalyzersVersions(), expectedVersionKeys),
                "analyzer versions values should be equal");

        Object[] expectedSymlinkKeys = TEST_MAPPED_SYMLINKS.keySet().stream().sorted().toArray();
        assertArrayEquals(expectedSymlinkKeys,
                vLatest.getIndexedSymlinks().keySet().stream().sorted().toArray(),
                "index symlinks keysets should be equal");
        assertArrayEquals(getMapValues(TEST_MAPPED_SYMLINKS, expectedSymlinkKeys),
                getMapValues(vLatest.getIndexedSymlinks(), expectedSymlinkKeys),
                "index symlinks values should be equal");
    }

    @Test
    public void shouldUpgradeV2() throws IOException, ClassNotFoundException {
        IndexAnalysisSettings obj = new IndexAnalysisSettings();
        obj.setAnalyzerGuruVersion(ANALYZER_GURU_VERSION);
        Map<String, Long> actAnalyzersVersionNos = AnalyzerGuru.getAnalyzersVersionNos();
        obj.setAnalyzersVersions(actAnalyzersVersionNos);
        obj.setProjectName(PROJECT_NAME);
        obj.setTabSize(TABSIZE);
        obj.setAnalyzersVersions(actAnalyzersVersionNos);
        byte[] bin = obj.serialize();

        IndexAnalysisSettingsUpgrader upgrader = new IndexAnalysisSettingsUpgrader();
        IndexAnalysisSettings3 v3 = upgrader.upgrade(bin, 2);
        assertNotNull(v3, "should get non-null from upgrader");
        assertEquals(PROJECT_NAME, v3.getProjectName(), "should have same projectName");
        assertEquals(TABSIZE, (int) v3.getTabSize(), "should have same tabSize");
        assertEquals(ANALYZER_GURU_VERSION, (long) v3.getAnalyzerGuruVersion(), "should have same analyzerGuruVersion");
        assertEquals(v3.getAnalyzersVersions().size(), actAnalyzersVersionNos.size(),
                "should have expected analyzer versions");
        assertTrue(v3.getIndexedSymlinks().isEmpty(), "should have no indexedSymlinks");
    }

    @Test
    public void shouldThrowIfVersionIsMisrepresented() throws IOException, ClassNotFoundException {
        IndexAnalysisSettings obj = new IndexAnalysisSettings();
        obj.setAnalyzerGuruVersion(ANALYZER_GURU_VERSION);
        obj.setProjectName(PROJECT_NAME);
        obj.setTabSize(TABSIZE);
        byte[] bin = obj.serialize();

        IndexAnalysisSettingsUpgrader upgrader = new IndexAnalysisSettingsUpgrader();
        assertThrows(InvalidClassException.class, () -> upgrader.upgrade(bin, 3),
                "should not have produced an instance");
    }

    @Test
    public void shouldThrowIfTooOldVersion() throws ClassNotFoundException {
        boolean passed = false;

        IndexAnalysisSettingsUpgrader upgrader = new IndexAnalysisSettingsUpgrader();
        try {
            upgrader.upgrade(new byte[0], 1);
        } catch (IOException e) {
            // expected
            assertTrue(e.getMessage().startsWith("Too old version"));
            passed = true;
        }
        assertTrue(passed, "should have thrown on too-old version");
    }

    private static <K, V> Object[] getMapValues(Map<K, V> map, Object[] keys) {
        Object[] values = new Object[keys.length];
        for (int i = 0; i < keys.length; ++i) {
            //noinspection SuspiciousMethodCalls
            values[i] = map.get(keys[i]);
        }
        return values;
    }
}
