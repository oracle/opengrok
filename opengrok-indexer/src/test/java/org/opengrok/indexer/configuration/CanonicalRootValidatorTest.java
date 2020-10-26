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
package org.opengrok.indexer.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.UnixPresent;

import java.io.File;

public class CanonicalRootValidatorTest {

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @Test
    public void testRejectUnseparated() {
        assertEquals("test value must end with a separator",
                CanonicalRootValidator.validate("test", "test value"));
    }

    @Test
    @ConditionalRun(UnixPresent.class)
    public void testRejectRoot() {
        assertEquals("should reject root", "test value cannot be the root directory",
                CanonicalRootValidator.validate("/", "test value"));
    }

    @Test
    public void testRejectWindowsRoot() {
        assertEquals("should reject Windows root", "--canonicalRoot cannot be a root directory",
                CanonicalRootValidator.validate("C:" + File.separator, "--canonicalRoot"));
    }

    @Test
    public void testSlashVar() {
        assertNull("should allow /var/",
                CanonicalRootValidator.validate(File.separator + "var" + File.separator,
                        "--canonicalRoot"));
    }
}
