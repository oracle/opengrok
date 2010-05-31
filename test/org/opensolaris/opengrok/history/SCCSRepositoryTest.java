/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.opensolaris.opengrok.history;

import java.io.File;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Lubos Kosco
 */
public class SCCSRepositoryTest {

    public SCCSRepositoryTest() {
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
     * Test of isRepositoryFor method, of class SCCSRepository.
     */
    @Test
    public void testIsRepositoryFor() {
        //test bug 15954
        File tdir = new File(System.getProperty("java.io.tmpdir")+File.separator+"testogrepo");
        File test = new File(tdir,"Codemgr_wsdata");
        test.mkdirs();
        SCCSRepository instance = new SCCSRepository();        
        assertTrue(instance.isRepositoryFor(tdir));
        test.delete();
        tdir.delete();
    }

}