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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 *
 * @author shaehn
 */
public class RepositoryInfoTest {
    
    private static String SOURCE_ROOT = "/path/to/source/root";
    private static String RELATIVE = "/relativeName";
    private static RepositoryInfo r;
    private static String device = "";
    
    public RepositoryInfoTest() {
        if (File.separator.equals("\\")) {  // only on Windows
            device = "C:";
        }
    }
    
    @BeforeClass
    public static void setUpClass() {
        RuntimeEnvironment.getInstance().setSourceRoot(SOURCE_ROOT);
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
        r = new RepositoryInfo();
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Testing results of setting directory name when only 
     * supplying a relative path to setDirectoryName function.
     */
    @Test
    public void setDirectoryWithRelativeName() {
        File fullRepositoryPath = new File(SOURCE_ROOT + RELATIVE);
        r.setDirectoryName(RELATIVE);
        
        assertEquals(fullRepositoryPath.getAbsolutePath(), device + r.getDirectoryName());
        assertEquals(SOURCE_ROOT, r.getSourceRoot());
        assertEquals(RELATIVE, r.getDirectoryNameRelative());
    }
    
    /**
     * Tests setting directory will full path to repository.
     */
    @Test
    public void setDirectoryWithFullRepositoryPath() {
        File fullRepositoryPath = new File(SOURCE_ROOT + RELATIVE);
        r.setDirectoryName(SOURCE_ROOT + RELATIVE);
        
        assertEquals(fullRepositoryPath.getAbsolutePath(), device + r.getDirectoryName());
        assertEquals(SOURCE_ROOT, r.getSourceRoot());
        assertEquals(RELATIVE, r.getDirectoryNameRelative());
    }
    
    /**
     * Simulates getting an alternate source root via a symlink.
     */
    @Test
    public void setDirectoryWithAlternateSourceRoot() {
        String alternateSourceRoot = "/an/alternate/source/root";
        File alternateSourcePath = new File(alternateSourceRoot);
        File fullRepositoryPath = new File(alternateSourceRoot + RELATIVE);
        try {
            r.setDirectoryName(fullRepositoryPath.getCanonicalPath());
        } catch(IOException e) {
            fail("Unable to get canonical name of " + fullRepositoryPath.toString());
        }

        assertEquals(fullRepositoryPath.getAbsolutePath(), r.getDirectoryName());
        assertEquals(alternateSourcePath.getAbsolutePath(),r.getSourceRoot());
        assertEquals(RELATIVE, r.getDirectoryNameRelative());
    }
}
