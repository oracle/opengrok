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
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the SCCSget class.
 * @author Trond Norbye
 */
public class SCCSgetTest {

    private static boolean haveSccs = true;
    private File sccsfile;
    private File sccsdir;

    @BeforeAll
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
        try {
            p = Runtime.getRuntime().exec("sccs --version");
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

    @BeforeEach
    public void setUp() throws IOException {
        if (!haveSccs) {
            return;
        }
        try {
            sccsdir = File.createTempFile("s.test", "sccs");
            sccsdir.delete();
            assertTrue(sccsdir.mkdirs(), "Failed to set up the test-directory");
            sccsfile = new File(sccsdir, "s.note.txt");
            InputStream in = getClass().getResourceAsStream("/history/s.note.txt");
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

    @AfterEach
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
        ZipInputStream zstream = new ZipInputStream(getClass().getResourceAsStream("/history/sccs-revisions.zip"));
        ZipEntry entry;

        while ((entry = zstream.getNextEntry()) != null) {
            String expected = readInput(zstream);
            InputStream sccs = SCCSget.getRevision("sccs", sccsfile, entry.getName());
            String got = readInput(sccs);
            sccs.close();
            zstream.closeEntry();
            assertEquals(expected, got);
        }
        zstream.close();
    }
}
