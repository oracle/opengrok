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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Do basic testing of the IteratorReader class.
 * @author Trond Norbye
 */
public class IteratorReaderTest {

    /**
     * Test that we don't get an error when the list is empty.
     */
    @Test
    public void testBug3094() throws IOException {
        List<String> empty = Collections.emptyList();
        try (IteratorReader instance = new IteratorReader(empty)) {
            assertNotNull(instance);
            assertEquals(-1, instance.read());
        }
    }

    /**
     * Test that we get an error immediately when constructing a token stream
     * where the list is {@code null}.
     */
    @Test
    public void testFailfastOnNull() {
        assertThrows(IllegalArgumentException.class, () -> new IteratorReader((List<String>) null));
    }

    /**
     * Test that an {@code IllegalArgumentException} is thrown immediately also
     * when using the constructor that takes an {@code Iterator}.
     */
    @Test
    public void testFailfastOnNullIterator() {
        assertThrows(IllegalArgumentException.class, () -> new IteratorReader((Iterator<String>) null));
    }

    /**
     * Test that we see all tokens when the last element of the list
     * contains multiple tokens. List2TokenStream used to see only the
     * first token in the last element. Hash2TokenStream used to see all
     * tokens.
     */
    @Test
    public void testReadAllTokens() throws IOException {
        try (BufferedReader instance = new BufferedReader(new IteratorReader(Arrays.asList("abc.def", "ghi.jkl")))) {
            assertEquals("abc.def", instance.readLine());
            assertEquals("ghi.jkl", instance.readLine());
            assertNull(instance.readLine());
        }
    }
}
