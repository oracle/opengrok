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
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.web;

import org.junit.Test;

/**
 * Unit tests for the {@code SearchHelper} class.
 */
public class SearchHelperTest {
    
    /**
     * Test that calling destroy() on an uninitialized instance does not
     * fail. Used to fail with a NullPointerException. See bug #19232.
     */
    @Test
    public void testDestroyUninitializedInstance() {
        new SearchHelper().destroy();
    }
}
