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

/**
 * Represents a converter to upgrade earlier binary representations of
 * index-analysis-settings to the latest version.
 */
public class IndexAnalysisSettingsUpgrader {

    /**
     * De-serialize the specified {@code bytes}, and upgrade if necessary from
     * an older version to the current object version which is
     * {@link IndexAnalysisSettings2}.
     * @param bytes a defined instance
     * @param objver a value greater than or equal to 1
     * @return a defined instance
     * @throws ClassNotFoundException if class of a serialized object cannot be
     * found
     * @throws IOException if any of the usual Input/Output related exceptions
     */
    public IndexAnalysisSettings2 upgrade(byte[] bytes, int objver)
            throws ClassNotFoundException, IOException {
        switch (objver) {
            case 1:
                IndexAnalysisSettings old1 = IndexAnalysisSettings.deserialize(
                        bytes);
                return convertFromV1(old1);
            case 2:
                return IndexAnalysisSettings2.deserialize(bytes);
            default:
                throw new IllegalArgumentException("Unknown version " + objver);
        }
    }

    private IndexAnalysisSettings2 convertFromV1(IndexAnalysisSettings old1) {
        IndexAnalysisSettings2 res = new IndexAnalysisSettings2();
        res.setAnalyzerGuruVersion(old1.getAnalyzerGuruVersion());
        res.setProjectName(old1.getProjectName());
        res.setTabSize(old1.getTabSize());
        // Version 1 has no analyzersVersions, so nothing more to do.
        return res;
    }
}
