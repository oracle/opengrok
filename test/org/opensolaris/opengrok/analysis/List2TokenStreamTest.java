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
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Do basic testing of the List2TokenStream class
 *
 * @author Trond Norbye
 */
public class List2TokenStreamTest {

    public List2TokenStreamTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test that we don't get an error when the list is empty.
     */
    @Test
    public void testBug3094() throws IOException {
        List<String> empty = Collections.emptyList();
        List2TokenStream instance = new List2TokenStream(empty);
        assertNotNull(instance);
        assertFalse(instance.incrementToken());        
        instance.close();
    }

    /**
     * Test that we get an error immediately when constructing a token stream
     * where the list is {@code null}.
     */
    @Test
    public void testFailfastOnNull() {
        try {
            new List2TokenStream(null);
            fail("expected an exception");
        } catch (NullPointerException npe) {
            // expected
        }
    }
}
