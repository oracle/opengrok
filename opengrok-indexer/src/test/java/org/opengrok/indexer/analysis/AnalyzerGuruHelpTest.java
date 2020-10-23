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
package org.opengrok.indexer.analysis;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Represents a container for tests of {@link AnalyzerGuruHelp}.
 */
public class AnalyzerGuruHelpTest {
    @Test
    public void shouldCreateReadableUsage() {
        String usage = AnalyzerGuruHelp.getUsage();
        assertFalse("usage is not empty", usage.isEmpty());
        assertTrue("usage contains \"*.\"", usage.contains("*."));
        assertTrue("usage contains \"#!\"", usage.contains("#!"));
    }
}
