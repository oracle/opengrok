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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.history;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test the SCCSget class
 * @author Trond Norbye
 */
public class SCCSgetTest {

    private static boolean haveSccs = true;
    private File sccsfile;
    private File sccsdir;

    public SCCSgetTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Check to see if we have sccs..
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("sccs help help");
            p.waitFor();
            haveSccs = (p.exitValue() == 0);
        } catch (Exception e) {
            haveSccs = false;
        } finally {
            try {
                if (p != null) {
                    p.destroy();
                }
            } catch (Exception e) {
                
            }
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws IOException {
        if (!haveSccs) {
            return;
        }
        try {
            sccsdir = File.createTempFile("s.test", "sccs");
            sccsdir.delete();
            if (!sccsdir.mkdirs()) {
                fail("Failed to set up the test-directory");
            }
            sccsfile = new File(sccsdir, "s.note.txt");
            InputStream in = getClass().getResourceAsStream("s.note.txt");
            FileOutputStream out = new FileOutputStream(sccsfile);
            byte[] buffer = new byte[8192];
            int nr;

            while ((nr = in.read(buffer, 0, buffer.length)) != -1) {
                out.write(buffer, 0, nr);
            }
            out.flush();
            in.close();
            out.close();
        } catch (IOException ex) {
            if (sccsfile != null) {
                sccsfile.delete();
                sccsdir.delete();
            }
            throw ex;
        }
    }

    @After
    public void tearDown() {
        if (sccsfile != null) {
            sccsfile.delete();
        }

        if (sccsdir != null) {
            sccsdir.delete();
        }
    }

    private String readInput(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[32 * 1024];
        int len;

        while ((len = in.read(buffer)) != -1) {
            if (len > 0) {
                out.write(buffer, 0, len);
            }
        }

        return out.toString();
    }

    /**
     * Test of getRevision method, of class SCCSget.
     */
    @Test
    public void getRevision() throws Exception {
        if (!haveSccs) {
            System.out.println("sccs not available. Skipping test");
            return;
        }
        ZipInputStream zstream = new ZipInputStream(getClass().getResourceAsStream("sccs-revisions.zip"));
        ZipEntry entry;

        while ((entry = zstream.getNextEntry()) != null) {
            String expected = readInput(zstream);
            InputStream sccs = SCCSget.getRevision("sccs",sccsfile, entry.getName());
            String got = readInput(sccs);
            sccs.close();
            zstream.closeEntry();
            assertEquals(expected, got);
        }
        zstream.close();
    }
}
