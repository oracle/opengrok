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
 * Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.util.List;

/**
 * Represents a container for tests of {@link CtagsUtil}.
 */
public class CtagsUtilTest {

    @Test
    public void getLanguages() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        List<String> result = CtagsUtil.getLanguages(env.getCtags());
        assertNotNull("should get Ctags languages", result);
        assertTrue("Ctags languages should contains C++", result.contains("C++"));
        // Test that the [disabled] tag is stripped for OldC.
        assertTrue("Ctags languages should contains OldC", result.contains("OldC"));
    }
}
