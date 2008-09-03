package org.opensolaris.opengrok.history;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author austvik
 */
public class AnnotationTest {

    public AnnotationTest() {
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
     * Test of getRevision method, of class Annotation.
     */
    @Test
    public void getRevision() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(instance.getRevision(1), "");
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.getRevision(1), "1.0");
        instance.addLine("1.1.0", "Author 2", false);
        assertEquals(instance.getRevision(2), "1.1.0");
    }

    /**
     * Test of getAuthor method, of class Annotation.
     */
    @Test
    public void getAuthor() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(instance.getAuthor(1), "");
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.getAuthor(1), "Author");
        instance.addLine("1.1.0", "Author 2", false);
        assertEquals(instance.getAuthor(2), "Author 2");
    }

    /**
     * Test of isEnabled method, of class Annotation.
     */
    @Test
    public void isEnabled() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(instance.isEnabled(1), false);
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.isEnabled(1), true);
        instance.addLine("1.1.0", "Author 2", false);
        assertEquals(instance.isEnabled(2), false);
    }

    /**
     * Test of size method, of class Annotation.
     */
    @Test
    public void size() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(instance.size(), 0);
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.size(), 1);
        instance.addLine("1.1", "Author 2", true);
        assertEquals(instance.size(), 2);
    }

    /**
     * Test of getWidestRevision method, of class Annotation.
     */
    @Test
    public void getWidestRevision() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(instance.getWidestRevision(), 0);
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.getWidestRevision(), 3);
        instance.addLine("1.1.0", "Author 2", true);
        assertEquals(instance.getWidestRevision(), 5);
    }

    /**
     * Test of getWidestAuthor method, of class Annotation.
     */
    @Test
    public void getWidestAuthor() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(instance.getWidestAuthor(), 0);
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.getWidestAuthor(), 6);
        instance.addLine("1.1.0", "Author 2", false);
        assertEquals(instance.getWidestAuthor(), 8);
    }

    /**
     * Test of addLine method, of class Annotation.
     */
    @Test
    public void addLine() {
        Annotation instance = new Annotation("testfile.tst");
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.size(), 1);
    }

    /**
     * Test of getFilename method, of class Annotation.
     */
    @Test
    public void getFilename() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals("testfile.tst", instance.getFilename());
    }

}