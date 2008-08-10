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
package org.opensolaris.opengrok.index;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 *
 * @author Trond Norbye
 */
public class IndexerTest {

    private File sourceRoot;
    private File dataRoot;

    public IndexerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        File sourceBundle = null;
        try {
            sourceRoot = File.createTempFile("source", "opengrok");
            dataRoot = File.createTempFile("data", "opengrok");
            sourceBundle = File.createTempFile("srcbundle", ".zip");

            if (sourceRoot.exists()) {
                assertTrue(sourceRoot.delete());
            }

            if (dataRoot.exists()) {
                assertTrue(dataRoot.delete());
            }

            if (sourceBundle.exists()) {
                assertTrue(sourceBundle.delete());
            }

            assertTrue(sourceRoot.mkdirs());
            assertTrue(dataRoot.mkdirs());

            // unzip source-root
            InputStream in = IndexerTest.class.getResourceAsStream("source.zip");
            assertNotNull(in);
            FileOutputStream out = new FileOutputStream(sourceBundle);
            copyFile(in, out);
            out.close();
            extractArchive(sourceBundle);
        } catch (IOException ex) {
            fail("Failed to up the test-file");
        } finally {
            if (sourceBundle != null) {
                sourceBundle.delete();
            }
        }
    }

    @After
    public void tearDown() {
        if (sourceRoot != null) {
            removeDirs(sourceRoot);
        }
        if (dataRoot != null) {
            removeDirs(dataRoot);
        }
    }

    /**
     * Test of doIndexerExecution method, of class Indexer.
     */
    @Test
    public void testIndexGeneration() throws Exception {
        System.out.println("Generating index by using the class methods");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty("org.opensolaris.opengrok.configuration.ctags", "ctags"));
        if (env.validateExuberantCtags()) {
            env.setSourceRootFile(sourceRoot);
            env.setDataRoot(dataRoot);
            env.setVerbose(true);
            Indexer.getInstance().prepareIndexer(env, true, true, "/c", null, false, false, false, null, null);
            Indexer.getInstance().doIndexerExecution(true, 1, null, null);
        } else {
            System.out.println("Skipping test. Could not find a ctags I could use in path.");
        }
    }

    /**
     * Test of doIndexerExecution method, of class Indexer.
     */
    @Test
    public void testMain() throws IOException {
        System.out.println("Generate index by using command line options");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty("org.opensolaris.opengrok.configuration.ctags", "ctags"));
        if (env.validateExuberantCtags()) {
            String[] argv = { "-S", "-P", "-p", "/c", "-H", "-Q", "off", "-s", sourceRoot.getAbsolutePath(), "-d", dataRoot.getAbsolutePath(), "-v"};
            Indexer.main(argv);
        } else {
            System.out.println("Skipping test. Could not find a ctags I could use in path.");
        }
    }

    private void extractArchive(File sourceBundle) throws IOException {
        ZipFile zipfile = new ZipFile(sourceBundle);

        Enumeration<? extends ZipEntry> e = zipfile.entries();

        while (e.hasMoreElements()) {
            ZipEntry ze = e.nextElement();
            File file = new File(sourceRoot, ze.getName());
            if (ze.isDirectory()) {
                file.mkdirs();
            } else {
                InputStream in = zipfile.getInputStream(ze);
                assertNotNull(in);
                FileOutputStream out = new FileOutputStream(file);
                assertNotNull(out);
                copyFile(in, out);
            }
        }
    }

    private void removeDirs(File root) {
        for (File f : root.listFiles()) {
            if (f.isDirectory()) {
                removeDirs(f);
            } else {
                f.delete();
            }
        }
        root.delete();
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] array = new byte[8192];
        int nr;

        while ((nr = in.read(array)) > 0) {
            out.write(array, 0, nr);
        }
        out.flush();
    }
}