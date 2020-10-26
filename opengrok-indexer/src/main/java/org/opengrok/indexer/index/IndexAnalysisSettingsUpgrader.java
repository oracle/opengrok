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
 * Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.IOException;

/**
 * Represents a converter to upgrade earlier binary representations of
 * index-analysis-settings to the latest version.
 */
public class IndexAnalysisSettingsUpgrader {

    /**
     * De-serialize the specified {@code bytes}, and upgrade if necessary from
     * an older version to the current object version which is
     * {@link IndexAnalysisSettings3}.
     * @param bytes a defined instance
     * @param objectVersion a value greater than or equal to 1
     * @return a defined instance
     * @throws ClassNotFoundException if class of a serialized object cannot be
     * found
     * @throws IOException if any of the usual Input/Output related exceptions
     */
    public IndexAnalysisSettings3 upgrade(byte[] bytes, int objectVersion)
            throws ClassNotFoundException, IOException {
        switch (objectVersion) {
            case 1:
                throw new IOException("Too old version " + objectVersion);
            case 2:
                IndexAnalysisSettings old2 = IndexAnalysisSettings.deserialize(bytes);
                return convertFromV2(old2);
            case 3:
                return IndexAnalysisSettings3.deserialize(bytes);
            default:
                throw new IllegalArgumentException("Unknown version " + objectVersion);
        }
    }

    private IndexAnalysisSettings3 convertFromV2(IndexAnalysisSettings old2) {
        IndexAnalysisSettings3 res = new IndexAnalysisSettings3();
        res.setAnalyzerGuruVersion(old2.getAnalyzerGuruVersion());
        res.setAnalyzersVersions(old2.getAnalyzersVersions());
        res.setProjectName(old2.getProjectName());
        res.setTabSize(old2.getTabSize());
        // Version 2 has no indexedSymlinks, so nothing more to do.
        return res;
    }
}
