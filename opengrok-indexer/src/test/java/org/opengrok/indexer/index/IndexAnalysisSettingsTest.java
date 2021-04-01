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
package org.opengrok.indexer.index;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.search.QueryBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Represents a test class for {@link IndexAnalysisSettings}.
 */
public class IndexAnalysisSettingsTest {

    private static final String PROJECT_NAME = "foo-1-2-3";
    private static final long ANALYZER_GURU_VERSION = 3;
    private static final int TABSIZE = 17;
    private static final Map<String, Long> ANALYZER_VERSIONS = new HashMap<>();

    @BeforeAll
    public static void setUpClass() {
        ANALYZER_VERSIONS.put("abc", 6L);
        ANALYZER_VERSIONS.put("ABC", 45L);
        ANALYZER_VERSIONS.put("d e", Long.MAX_VALUE - 19);
    }

    @Test
    public void shouldAffirmINDEX_ANALYSIS_SETTINGS_OBJUID() {
        String objuid = QueryBuilder.normalizeDirPath("58859C75-F941-42E5-8D1A-FAF71DDEBBA7");
        assertEquals(objuid, IndexAnalysisSettingsAccessor.INDEX_ANALYSIS_SETTINGS_OBJUID,
                "IndexAnalysisSettingsDao objuid");
    }

    @Test
    public void shouldRoundTripANullObject() throws IOException, ClassNotFoundException {
        IndexAnalysisSettings obj = new IndexAnalysisSettings();
        byte[] bin = obj.serialize();

        IndexAnalysisSettings res = IndexAnalysisSettings.deserialize(bin);
        assertNotNull(res);
        assertNull(res.getProjectName(), "projectName");
        assertNull(res.getTabSize(), "tabSize");
        assertNull(res.getAnalyzerGuruVersion(), "analyzerGuruVersion");
    }

    @Test
    public void shouldRoundTripADefinedObject() throws IOException,
            ClassNotFoundException {
        IndexAnalysisSettings obj = new IndexAnalysisSettings();
        obj.setProjectName(PROJECT_NAME);
        obj.setAnalyzerGuruVersion(ANALYZER_GURU_VERSION);
        obj.setTabSize(TABSIZE);
        byte[] bin = obj.serialize();

        IndexAnalysisSettings res = IndexAnalysisSettings.deserialize(bin);
        assertNotNull(res);
        assertEquals(PROJECT_NAME, res.getProjectName(), "projectName");
        assertEquals(TABSIZE, (int) res.getTabSize(), "tabSize");
        assertEquals(ANALYZER_GURU_VERSION, (long) res.getAnalyzerGuruVersion(), "analyzerGuruVersion");
    }
}
