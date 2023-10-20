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
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.analysis;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.Scopes.Scope;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author Tomas Kotal
 */
class ScopesTest {

    /**
     * Test of getScope method, of class Scopes.
     */
    @Test
    void testGetScope() {
        Scopes instance = new Scopes();
        Scope globalScope = instance.getScope(0);

        instance.addScope(new Scope(10, 20, "scope1", "ns"));
        instance.addScope(new Scope(25, 30, "scope2", "ns"));
        instance.addScope(new Scope(40, 40, "scope3", "ns"));
        instance.addScope(new Scope(60, 70, "scope4", "ns"));
        instance.addScope(new Scope(80, 90, "scope5", "ns"));
        instance.addScope(new Scope(91, 100, "scope6", "ns"));

        assertEquals(6, instance.size());
        assertEquals(globalScope, instance.getScope(1));
        assertEquals("scope1", instance.getScope(10).getName());
        assertEquals("scope1", instance.getScope(15).getName());
        assertEquals("scope1", instance.getScope(20).getName());
        assertEquals(globalScope, instance.getScope(21));
        assertEquals(globalScope, instance.getScope(24));
        assertEquals(globalScope, instance.getScope(39));
        assertEquals("scope3", instance.getScope(40).getName());
        assertEquals(globalScope, instance.getScope(41));
        assertEquals("scope5", instance.getScope(90).getName());
        assertEquals("scope6", instance.getScope(100).getName());
        assertEquals(globalScope, instance.getScope(101));
        assertEquals(globalScope, instance.getScope(500));
    }

    @Test
    void testSerialize() throws IOException, ClassNotFoundException {
        Scopes scopes = new Scopes();
        scopes.addScope(new Scope(1, 100, "name", "namespace", "signature"));
        byte[] bytes = scopes.serialize();
        Scopes deserialized = Scopes.deserialize(bytes);
        assertEquals(1, deserialized.size());
    }

}
