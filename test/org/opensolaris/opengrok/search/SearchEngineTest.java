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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.search;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Do basic testing of the SearchEngine
 *
 * @author Trond Norbye
 */
public class SearchEngineTest {

    @Test
    public void testIsValidQuery() {
        SearchEngine instance = new SearchEngine();
        assertFalse(instance.isValidQuery());
        instance.setFile("foo");
        assertTrue(instance.isValidQuery());
    }

    @Test
    public void testDefinition() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getDefinition());
        String defs = "This is a definition";
        instance.setDefinition(defs);
        assertEquals(defs, instance.getDefinition());
    }

    @Test
    public void testFile() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getFile());
        String file = "This is a File";
        instance.setFile(file);
        assertEquals(file, instance.getFile());
    }

    @Test
    public void testFreetext() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getFreetext());
        String freetext = "This is just a piece of text";
        instance.setFreetext(freetext);
        assertEquals(freetext, instance.getFreetext());
    }

    @Test
    public void testHistory() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getHistory());
        String hist = "This is a piece of history";
        instance.setHistory(hist);
        assertEquals(hist, instance.getHistory());
    }

    @Test
    public void testSymbol() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getSymbol());
        String sym = "This is a symbol";
        instance.setSymbol(sym);
        assertEquals(sym, instance.getSymbol());
    }

    @Test
    public void testGetQuery() throws Exception {
        SearchEngine instance = new SearchEngine();
        instance.setHistory("Once upon a time");
        instance.setFile("Makefile");
        instance.setDefinition("std::string");
        instance.setSymbol("toString");
        instance.setFreetext("OpenGrok");
        assertTrue(instance.isValidQuery());
        assertEquals("+full:opengrok +defs:\"std string\" +refs:toString +path:makefile +(+hist:once +hist:upon +hist:time)",
                instance.getQuery());
    }
}