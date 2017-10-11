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
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.perl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the {@link PerlXref} class.
 */
public class PerlXrefTest {

    @Test
    public void sampleTest() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "org/opensolaris/opengrok/analysis/perl/sample.pl");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayOutputStream baosExp = new ByteArrayOutputStream();

        writePerlXref(res, new PrintStream(baos));
        res.close();

        InputStream exp = getClass().getClassLoader().getResourceAsStream(
                "org/opensolaris/opengrok/analysis/perl/samplePerlXrefRes.html");
        copyStream(exp, baosExp);
        exp.close();
        baosExp.close();
        baos.close();

        String ostr = new String(baos.toByteArray(), "UTF-8");
        String gotten[] = ostr.split("\n");

        String estr = new String(baosExp.toByteArray(), "UTF-8");
        String expected[] = estr.split("\n");

        for (int i = 0; i < expected.length && i < gotten.length; i++) {
            assertEquals("line " + (i + 1) + " diff", expected[i], gotten[i]);
        }

        assertEquals(expected.length, gotten.length);
    }

    private void writePerlXref(InputStream iss, PrintStream oss) throws IOException {
        InputStream begin = getClass().getClassLoader().getResourceAsStream(
            "org/opensolaris/opengrok/analysis/perl/samplePerlXrefBeginHtmlfrag.txt");
        copyStream(begin, oss);

        Writer sw = new StringWriter();
        PerlAnalyzer.writeXref(new InputStreamReader(iss, "UTF-8"), sw, null,
            null, null);
        oss.print(sw.toString());

        InputStream end = getClass().getClassLoader().getResourceAsStream(
            "org/opensolaris/opengrok/analysis/perl/samplePerlXrefEndHtmlfrag.txt");
        copyStream(end, oss);
    }

    private void copyStream(InputStream is, OutputStream os) throws IOException {
        byte buffer[] = new byte[8192];
        int read;
        do {
            read = is.read(buffer, 0, buffer.length);
            if (read > 0) {
                os.write(buffer, 0, read);
            }
        } while (read >= 0);
    }
}
