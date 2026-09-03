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
 * Copyright (c) 2026, nishank.soni <soninishank8@gmail.com>.
 */
package org.opengrok.indexer.analysis.yang;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.XrefTestBase;

/**
 * Tests for {@link YangXref}.
 */
class YangXrefTest extends XrefTestBase {

    @Test
    @SuppressWarnings("squid:S2699")
    void sampleTest() throws IOException {
        writeAndCompare(new YangAnalyzerFactory(),
                "analysis/yang/sample.yang",
                "analysis/yang/sample_xref.html", null, 10);
    }

    @Test
    @SuppressWarnings("squid:S2699")
    void shouldCloseTruncatedStringSpan() throws IOException {
        writeAndCompare(new YangAnalyzerFactory(),
                "analysis/yang/truncated.yang",
                "analysis/yang/truncated_xref.html", null, 1);
    }
}
