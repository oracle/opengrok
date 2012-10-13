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
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * JUnit test to test the EftarFile-system
 */
public class EftarFileTest {

    private static File tsv;
    private static File eftar;

    public EftarFileTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        tsv = File.createTempFile("paths", ".tsv");
        eftar = File.createTempFile("paths", ".eftar");

        PrintWriter out = null;
        try {
            out = new PrintWriter(new FileWriter(tsv));
            StringBuilder sb = new StringBuilder();
            for (int ii = 0; ii < 100; ii++) {
                sb.append("/path");
                sb.append(Integer.toString(ii));
                out.print(sb.toString());
                out.print("\tDescription ");
                out.println(Integer.toString(ii));
            }
            out.flush();
        } finally {
            try { out.close(); } catch (Exception e) { }
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (tsv != null) {
            tsv.delete();
        }

        if (eftar != null) {
            eftar.delete();
        }
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test creation of an EftarFile
     * @throws java.lang.Exception if an error occurs while creating the
     *                             eftar file
     */
    @Test
    public void createEftarFile() throws Exception {
        String[] args = new String[2];
        args[0] = tsv.getAbsolutePath();
        args[1] = eftar.getAbsolutePath();

        EftarFile ef = new EftarFile();
        ef.create(args);
    }

    /**
     * Test usage of an EftarFile
     * @throws IOException if an error occurs while accessing the eftar file
     */
    @Test
    public void searchEftarFile() throws IOException {
        searchEftarFile(new EftarFileReader(eftar));
        searchEftarFile(new EftarFileReader(eftar.getAbsolutePath()));
    }

    private void searchEftarFile(EftarFileReader er) throws IOException {
        StringBuilder sb = new StringBuilder();
        StringBuilder match = new StringBuilder();
        match.append("Description ");
        int offset = match.length();
        for (int ii = 0; ii < 100; ii++) {
            sb.append("/path");
            sb.append(Integer.toString(ii));
            match.setLength(offset);
            match.append(Integer.toString(ii));

            assertEquals(match.toString(), er.get(sb.toString()));
        }
        er.close();
    }
}
