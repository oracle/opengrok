package org.opensolaris.opengrok.analysis;

import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.opensolaris.opengrok.analysis.Definitions.Tag;

/**
 *
 * @author austvik
 */
public class DefinitionsTest {

    public DefinitionsTest() {
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
     * Test of getSymbols method, of class Definitions.
     */
    @Test
    public void getSymbols() {
        Definitions instance = new Definitions();
        Set<String> result = instance.getSymbols();
        assertNotNull(result);
        assertEquals(result.size(), 0);
        instance.addTag(1, "found", "", "");
        result = instance.getSymbols();
        assertNotNull(result);
        assertEquals(result.size(), 1);
    }

    /**
     * Test of hasSymbol method, of class Definitions.
     */
    @Test
    public void hasSymbol() {
        Definitions instance = new Definitions();
        instance.addTag(1, "found", "", "");
        assertEquals(instance.hasSymbol("notFound"), false);
        assertEquals(instance.hasSymbol("found"), true);
    }

    /**
     * Test of hasDefinitionAt method, of class Definitions.
     */
    @Test
    public void hasDefinitionAt() {
        Definitions instance = new Definitions();
        instance.addTag(1, "found", "", "");
        assertEquals(instance.hasDefinitionAt("found", 0), false);
        assertEquals(instance.hasDefinitionAt("found", 1), true);
        assertEquals(instance.hasDefinitionAt("found", 2), false);
        assertEquals(instance.hasDefinitionAt("notFound", 0), false);
        assertEquals(instance.hasDefinitionAt("notFound", 1), false);
    }

    /**
     * Test of occurrences method, of class Definitions.
     */
    @Test
    public void occurrences() {
        Definitions instance = new Definitions();
        instance.addTag(1, "one", "", "");
        instance.addTag(1, "two", "", "");
        instance.addTag(3, "two", "", "");
        assertEquals(instance.occurrences("one"), 1);
        assertEquals(instance.occurrences("two"), 2);
        assertEquals(instance.occurrences("notFound"), 0);
    }

    /**
     * Test of numberOfSymbols method, of class Definitions.
     */
    @Test
    public void numberOfSymbols() {
        Definitions instance = new Definitions();
        assertEquals(instance.numberOfSymbols(), 0);
        instance.addTag(1, "one", "", "");
        assertEquals(instance.numberOfSymbols(), 1);
        instance.addTag(1, "two", "", "");
        instance.addTag(3, "two", "", "");
        assertEquals(instance.numberOfSymbols(), 2);
    }

    /**
     * Test of getTags method, of class Definitions.
     */
    @Test
    public void getTags() {
        Definitions instance = new Definitions();
        assertEquals(instance.getTags().size(), 0);
        instance.addTag(1, "one", "", "");
        assertEquals(instance.getTags().size(), 1);
        instance.addTag(1, "two", "", "");
        assertEquals(instance.getTags().size(), 2);
        instance.addTag(3, "two", "", "");
        assertEquals(instance.getTags().size(), 3);
    }

    /**
     * Test of addTag method, of class Definitions.
     */
    @Test
    public void addTag() {
        Definitions instance = new Definitions();
        assertEquals(instance.getTags().size(), 0);
        instance.addTag(1, "one", "", "");
        assertEquals(instance.getTags().size(), 1);
    }

    /**
     * Test of serialize method, of class Definitions.
     */
    @Test
    public void serialize() throws Exception {
        Definitions instance = new Definitions();
        instance.addTag(1, "one", "", "");
        byte serial[] = instance.serialize();
        Definitions instance2 = Definitions.deserialize(serial);
        assertEquals(instance.getTags().size(), instance2.getTags().size());
        assertEquals(instance.getSymbols().size(), instance2.getSymbols().size());
    }


}