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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.archive.ZipAnalyzer;
import org.opensolaris.opengrok.analysis.c.CxxAnalyzerFactory;
import org.opensolaris.opengrok.analysis.executables.JarAnalyzer;
import org.opensolaris.opengrok.analysis.perl.PerlAnalyzer;
import org.opensolaris.opengrok.analysis.plain.PlainAnalyzer;
import org.opensolaris.opengrok.analysis.plain.XMLAnalyzer;
import org.opensolaris.opengrok.analysis.sh.ShAnalyzer;
import org.opensolaris.opengrok.analysis.sh.ShAnalyzerFactory;
import static org.junit.Assert.*;

/**
 * Tests for the functionality provided by the AnalyzerGuru class.
 */
public class AnalyzerGuruTest {
    /**
     * Test that we get the correct analyzer if the file name exactly matches a
     * known extension.
     */
    @Test
    public void testFileNameSameAsExtension() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "#!/bin/sh\nexec /usr/bin/zip \"$@\"\n".getBytes("US-ASCII"));
        String file = "/dummy/path/to/source/zip";
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, file);
        assertSame(ShAnalyzer.class, fa.getClass());
    }

    @Test
    public void testUTF8ByteOrderMark() throws Exception {
        byte[] xml = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, // UTF-8 BOM
                       '<', '?', 'x', 'm', 'l', ' ',
                       'v', 'e', 'r', 's', 'i', 'o', 'n', '=',
                       '"', '1', '.', '0', '"', '?', '>'};
        ByteArrayInputStream in = new ByteArrayInputStream(xml);
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame(XMLAnalyzer.class, fa.getClass());
    }

    @Test
    public void testUTF8ByteOrderMarkPlusCopyrightSymbol() throws Exception {
        byte[] doc = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, // UTF-8 BOM
                       '/', '/', ' ', (byte) 0xC2, (byte)0xA9};
        ByteArrayInputStream in = new ByteArrayInputStream(doc);
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame("despite BOM as precise match,", PlainAnalyzer.class,
            fa.getClass());
    }

    @Test
    public void testUTF8ByteOrderMarkPlainFile() throws Exception {
        byte[] bytes = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, // UTF-8 BOM
                       'h', 'e', 'l', 'l', 'o', ' ',
                       'w', 'o', 'r', 'l', 'd'};
        
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame(PlainAnalyzer.class, fa.getClass());
    }

    @Test
    public void testUTF16BigByteOrderMarkPlusCopyrightSymbol() throws Exception {
        byte[] doc = {(byte) 0xFE, (byte) 0xFF, // UTF-16BE BOM
                       '/', '/', ' ', (byte) 0xC2, (byte) 0xA9};
        ByteArrayInputStream in = new ByteArrayInputStream(doc);
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame("despite BOM as precise match,", PlainAnalyzer.class,
            fa.getClass());
    }

    @Test
    public void testUTF16LittleByteOrderMarkPlusCopyrightSymbol() throws Exception {
        byte[] doc = {(byte) 0xFF, (byte) 0xFE, // UTF-16BE BOM
                       '/', '/', ' ', (byte) 0xA9, (byte) 0xC2};
        ByteArrayInputStream in = new ByteArrayInputStream(doc);
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame("despite BOM as precise match,", PlainAnalyzer.class,
            fa.getClass());
    }

    @Test
    public void addExtension() throws Exception {
        // should not find analyzer for this unlikely extension
        assertNull(AnalyzerGuru.find("file.unlikely_extension"));

        FileAnalyzerFactory
            faf = AnalyzerGuru.findFactory(ShAnalyzerFactory.class.getName());
        // should be the same factory as the built-in analyzer for sh scripts
        assertSame(AnalyzerGuru.find("myscript.sh"), faf);

        // add an analyzer for the extension and see that it is picked up
        AnalyzerGuru.addExtension("UNLIKELY_EXTENSION", faf);
        assertSame(ShAnalyzerFactory.class,
                   AnalyzerGuru.find("file.unlikely_extension").getClass());

        // remove the mapping and verify that it is gone
        AnalyzerGuru.addExtension("UNLIKELY_EXTENSION", null);
        assertNull(AnalyzerGuru.find("file.unlikely_extension"));
    }

    @Test
    public void addPrefix() throws Exception {
        // should not find analyzer for this unlikely extension
        assertNull(AnalyzerGuru.find("unlikely_prefix.foo"));

        FileAnalyzerFactory
            faf = AnalyzerGuru.findFactory(ShAnalyzerFactory.class.getName());
        // should be the same factory as the built-in analyzer for sh scripts
        assertSame(AnalyzerGuru.find("myscript.sh"), faf);

        // add an analyzer for the prefix and see that it is picked up
        AnalyzerGuru.addPrefix("UNLIKELY_PREFIX", faf);
        assertSame(ShAnalyzerFactory.class,
                   AnalyzerGuru.find("unlikely_prefix.foo").getClass());

        // remove the mapping and verify that it is gone
        AnalyzerGuru.addPrefix("UNLIKELY_PREFIX", null);
        assertNull(AnalyzerGuru.find("unlikely_prefix.foo"));
    }

    @Test
    public void testZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("dummy"));
        zos.closeEntry();
        zos.close();
        InputStream in = new ByteArrayInputStream(baos.toByteArray());
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "dummy");
        assertSame(ZipAnalyzer.class, fa.getClass());
    }

    @Test
    public void testJar() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JarOutputStream jos = new JarOutputStream(baos);
        jos.putNextEntry(new JarEntry("dummy"));
        jos.closeEntry();
        jos.close();
        InputStream in = new ByteArrayInputStream(baos.toByteArray());
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "dummy");
        assertSame(JarAnalyzer.class, fa.getClass());
    }

    @Test
    public void testPlainText() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "This is a plain text file.".getBytes("US-ASCII"));
        assertSame(PlainAnalyzer.class,
                   AnalyzerGuru.getAnalyzer(in, "dummy").getClass());
    }

    @Test
    public void rfe2969() {
        FileAnalyzerFactory faf = AnalyzerGuru.find("foo.hxx");
        assertNotNull(faf);
        assertSame(CxxAnalyzerFactory.class, faf.getClass());
    }

    @Test
    public void rfe3401() {
        FileAnalyzerFactory f1 = AnalyzerGuru.find("main.c");
        assertNotNull(f1);
        FileAnalyzerFactory f2 = AnalyzerGuru.find("main.cc");
        assertNotNull(f2);
        assertNotSame(f1.getClass(), f2.getClass());

    }

    /**
     * Test that matching of full names works. Bug #859.
     */
    @Test
    public void matchesFullName() {
        FileAnalyzerFactory faf = AnalyzerGuru.find("/path/to/Makefile");
        assertSame(ShAnalyzerFactory.class, faf.getClass());
        faf = AnalyzerGuru.find("GNUMakefile");
        assertSame(ShAnalyzerFactory.class, faf.getClass());
    }

    @Test
    public void shouldMatchPerlHashbang() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "#!/usr/bin/perl -w".getBytes("US-ASCII"));
        assertSame("despite Perl hashbang,", PerlAnalyzer.class,
            AnalyzerGuru.getAnalyzer(in, "dummy").getClass());
    }

    @Test
    public void shouldMatchPerlHashbangSpaced() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "\n\t #!  /usr/bin/perl -w".getBytes("US-ASCII"));
        assertSame("despite Perl hashbang,", PerlAnalyzer.class,
            AnalyzerGuru.getAnalyzer(in, "dummy").getClass());
    }

    @Test
    public void shouldMatchEnvPerlHashbang() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "#!/usr/bin/env perl -w".getBytes("US-ASCII"));
        assertSame("despite env hashbang with perl,", PerlAnalyzer.class,
            AnalyzerGuru.getAnalyzer(in, "dummy").getClass());
    }

    @Test
    public void shouldMatchEnvPerlHashbangSpaced() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "\n\t #!  /usr/bin/env\t perl -w".getBytes("US-ASCII"));
        assertSame("despite env hashbang with perl,", PerlAnalyzer.class,
            AnalyzerGuru.getAnalyzer(in, "dummy").getClass());
    }

    @Test
    public void shouldNotMatchEnvLFPerlHashbang() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "#!/usr/bin/env\nperl".getBytes("US-ASCII"));
        assertNotSame("despite env hashbang LF,", PerlAnalyzer.class,
            AnalyzerGuru.getAnalyzer(in, "dummy").getClass());
    }
}
