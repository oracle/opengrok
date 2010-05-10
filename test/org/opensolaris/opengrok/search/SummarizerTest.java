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
 * Copyright 2010 Sun Micosystems.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.search;

import org.apache.lucene.search.Query;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.CompatibleAnalyser;
import static org.junit.Assert.*;

/**
 * Unit tests for the Summarizer class.
 */
public class SummarizerTest {
    /**
     * If the last token in a text fragment is a token we're searching for,
     * and that token is also present earlier in the fragment, getSummary()
     * used to throw a StringIndexOutOfBoundsException. Bug #15858.
     */
    @Test
    public void bug15858() throws Exception {
        Query query = new QueryBuilder().setFreetext("beta").build();
        Summarizer instance = new Summarizer(query, new CompatibleAnalyser());
        // This call used to result in a StringIndexOutOfBoundsException
        assertNotNull(instance.getSummary("alpha beta gamma delta beta"));
    }
}
