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
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author austvik
 */
class DefinitionsTest {

    /**
     * Test of getSymbols method, of class Definitions.
     */
    @Test
    void getSymbols() {
        Definitions instance = new Definitions();
        Set<String> result = instance.getSymbols();
        assertNotNull(result);
        assertEquals(0, result.size());
        instance.addTag(1, "found", "", "", 0, 0);
        result = instance.getSymbols();
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    /**
     * Test of hasSymbol method, of class Definitions.
     */
    @Test
    void hasSymbol() {
        Definitions instance = new Definitions();
        instance.addTag(1, "found", "", "", 0, 0);
        assertFalse(instance.hasSymbol("notFound"));
        assertTrue(instance.hasSymbol("found"));
    }

    /**
     * Test of hasDefinitionAt method, of class Definitions.
     */
    @Test
    void hasDefinitionAt() {
        Definitions instance = new Definitions();
        String[] type = {""};
        instance.addTag(1, "found", "", "", 0, 0);
        assertFalse(instance.hasDefinitionAt("found", 0, type));
        assertTrue(instance.hasDefinitionAt("found", 1, type));
        assertFalse(instance.hasDefinitionAt("found", 2, type));
        assertFalse(instance.hasDefinitionAt("notFound", 0, type));
        assertFalse(instance.hasDefinitionAt("notFound", 1, type));
    }

    /**
     * Test of occurrences method, of class Definitions.
     */
    @Test
    void occurrences() {
        Definitions instance = new Definitions();
        instance.addTag(1, "one", "", "", 0, 0);
        instance.addTag(1, "two", "", "", 0, 0);
        instance.addTag(3, "two", "", "", 0, 0);
        assertEquals(1, instance.occurrences("one"));
        assertEquals(2, instance.occurrences("two"));
        assertEquals(0, instance.occurrences("notFound"));
    }

    /**
     * Test of numberOfSymbols method, of class Definitions.
     */
    @Test
    void numberOfSymbols() {
        Definitions instance = new Definitions();
        assertEquals(0, instance.numberOfSymbols());
        instance.addTag(1, "one", "", "", 0, 0);
        assertEquals(1, instance.numberOfSymbols());
        instance.addTag(1, "two", "", "", 0, 0);
        instance.addTag(3, "two", "", "", 0, 0);
        assertEquals(2, instance.numberOfSymbols());
    }

    /**
     * Test of getTags method, of class Definitions.
     */
    @Test
    void getTags() {
        Definitions instance = new Definitions();
        assertEquals(0, instance.getTags().size());
        instance.addTag(1, "one", "", "", 0, 0);
        assertEquals(1, instance.getTags().size());
        instance.addTag(1, "two", "", "", 0, 0);
        assertEquals(2, instance.getTags().size());
        instance.addTag(3, "two", "", "", 0, 0);
        assertEquals(3, instance.getTags().size());
    }

    /**
     * Test of addTag method, of class Definitions.
     */
    @Test
    void addTag() {
        Definitions instance = new Definitions();
        assertEquals(0, instance.getTags().size());
        instance.addTag(1, "one", "", "", 0, 0);
        assertEquals(1, instance.getTags().size());
    }

    /**
     * Test of serialize method, of class Definitions.
     */
    @Test
    void serialize() throws Exception {
        Definitions instance = new Definitions();
        instance.addTag(1, "one", "", "", 0, 0);
        byte[] serial = instance.serialize();
        Definitions deserializedInstance = Definitions.deserialize(serial);
        assertEquals(instance.getTags().size(), deserializedInstance.getTags().size());
        assertEquals(instance.getSymbols().size(), deserializedInstance.getSymbols().size());
    }

}
