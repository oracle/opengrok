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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.index;

import java.io.IOException;
import java.util.Map;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.opengrok.analysis.AnalyzerGuru;

/**
 * Represents a test container for {@link IndexAnalysisSettingsUpgrader}.
 */
public class IndexAnalysisSettingsUpgraderTest {

    private static final String PROJECT_NAME = "foo-1-2-3";
    private static final long ANALYZER_GURU_VERSION = 3;
    private static final int TABSIZE = 17;

    @Test
    public void shouldHandleLatest() throws IOException,
            ClassNotFoundException {
        IndexAnalysisSettings2 obj = new IndexAnalysisSettings2();
        obj.setAnalyzerGuruVersion(ANALYZER_GURU_VERSION);
        Map<String, Long> actAnalyzersVersionNos =
                AnalyzerGuru.getAnalyzersVersionNos();
        obj.setAnalyzersVersions(actAnalyzersVersionNos);
        obj.setProjectName(PROJECT_NAME);
        obj.setTabSize(TABSIZE);
        byte[] bin = obj.serialize();

        IndexAnalysisSettingsUpgrader upgrader =
                new IndexAnalysisSettingsUpgrader();
        IndexAnalysisSettings2 vlatest = upgrader.upgrade(bin, 2);
        assertNotNull(vlatest);
        assertEquals("projectName", PROJECT_NAME, vlatest.getProjectName());
        assertEquals("tabSize", TABSIZE, (int)vlatest.getTabSize());
        assertEquals("analyzerGuruVersion", ANALYZER_GURU_VERSION,
            (long)vlatest.getAnalyzerGuruVersion());
        assertTrue("should have expected analyzer versions",
            vlatest.getAnalyzersVersions().size() ==
            actAnalyzersVersionNos.size());
        /**
         * Smelly but I know the underlying Map implementations are the same;
         * otherwise the following tests might not work.
         */
        assertArrayEquals(actAnalyzersVersionNos.keySet().toArray(),
            vlatest.getAnalyzersVersions().keySet().toArray());
        assertArrayEquals(actAnalyzersVersionNos.values().toArray(),
            vlatest.getAnalyzersVersions().values().toArray());
    }

    @Test
    public void shouldUpgradeV1() throws IOException, ClassNotFoundException {
        IndexAnalysisSettings obj = new IndexAnalysisSettings();
        obj.setAnalyzerGuruVersion(ANALYZER_GURU_VERSION);
        obj.setProjectName(PROJECT_NAME);
        obj.setTabSize(TABSIZE);
        byte[] bin = obj.serialize();

        IndexAnalysisSettingsUpgrader upgrader =
                new IndexAnalysisSettingsUpgrader();
        IndexAnalysisSettings2 v2 = upgrader.upgrade(bin, 1);
        assertNotNull(v2);
        assertEquals("projectName", PROJECT_NAME, v2.getProjectName());
        assertEquals("tabSize", TABSIZE, (int)v2.getTabSize());
        assertEquals("analyzerGuruVersion", ANALYZER_GURU_VERSION,
            (long)v2.getAnalyzerGuruVersion());
        assertTrue("should have no analyzer versions",
            v2.getAnalyzersVersions().isEmpty());
    }

    @Test
    public void shouldThrowIfVersionIsMisrepresented() throws IOException,
            ClassNotFoundException {
        IndexAnalysisSettings obj = new IndexAnalysisSettings();
        obj.setAnalyzerGuruVersion(ANALYZER_GURU_VERSION);
        obj.setProjectName(PROJECT_NAME);
        obj.setTabSize(TABSIZE);
        byte[] bin = obj.serialize();

        IndexAnalysisSettingsUpgrader upgrader =
                new IndexAnalysisSettingsUpgrader();
        IndexAnalysisSettings2 res = null;
        try {
            res = upgrader.upgrade(bin, 2); // wrong objver
        } catch (ClassCastException e) {
            // expected
        }
        assertNull("should not have produced an instance", res);
    }
}
