/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opensolaris.opengrok.analysis;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.opensolaris.opengrok.analysis.Scopes.Scope;

/**
 *
 * @author kotal
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
        assertEquals(instance.getScope(10).name, "scope1");
        assertEquals(instance.getScope(15).name, "scope1");
        assertEquals(instance.getScope(20).name, "scope1");
        assertEquals(instance.getScope(21), globalScope);
        assertEquals(instance.getScope(24), globalScope);
        assertEquals(instance.getScope(39), globalScope);
        assertEquals(instance.getScope(40).name, "scope3");
        assertEquals(instance.getScope(41), globalScope);
        assertEquals(instance.getScope(90).name, "scope5");
        assertEquals(instance.getScope(100).name, "scope6");
        assertEquals(instance.getScope(101), globalScope);
        assertEquals(instance.getScope(500), globalScope);
    }
    
}
