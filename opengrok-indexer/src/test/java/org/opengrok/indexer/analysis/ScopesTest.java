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
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.analysis;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opengrok.indexer.analysis.Scopes.Scope;

/**
 *
 * @author Tomas Kotal
 */
public class ScopesTest {

    /**
     * Test of getScope method, of class Scopes.
     */
    @Test
    public void testGetScope() {
        Scopes instance = new Scopes();
        Scope globalScope = instance.getScope(0);

        instance.addScope(new Scope(10, 20, "scope1", "ns"));
        instance.addScope(new Scope(25, 30, "scope2", "ns"));
        instance.addScope(new Scope(40, 40, "scope3", "ns"));
        instance.addScope(new Scope(60, 70, "scope4", "ns"));
        instance.addScope(new Scope(80, 90, "scope5", "ns"));
        instance.addScope(new Scope(91, 100, "scope6", "ns"));

        assertEquals(instance.size(), 6);
        assertEquals(instance.getScope(1), globalScope);
        assertEquals(instance.getScope(10).getName(), "scope1");
        assertEquals(instance.getScope(15).getName(), "scope1");
        assertEquals(instance.getScope(20).getName(), "scope1");
        assertEquals(instance.getScope(21), globalScope);
        assertEquals(instance.getScope(24), globalScope);
        assertEquals(instance.getScope(39), globalScope);
        assertEquals(instance.getScope(40).getName(), "scope3");
        assertEquals(instance.getScope(41), globalScope);
        assertEquals(instance.getScope(90).getName(), "scope5");
        assertEquals(instance.getScope(100).getName(), "scope6");
        assertEquals(instance.getScope(101), globalScope);
        assertEquals(instance.getScope(500), globalScope);
    }
}
