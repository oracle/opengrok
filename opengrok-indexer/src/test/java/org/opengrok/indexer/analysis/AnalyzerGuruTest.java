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
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Assert;
import org.junit.Test;
import org.opengrok.indexer.analysis.archive.ZipAnalyzer;
import org.opengrok.indexer.analysis.c.CxxAnalyzerFactory;
import org.opengrok.indexer.analysis.document.MandocAnalyzer;
import org.opengrok.indexer.analysis.document.TroffAnalyzer;
import org.opengrok.indexer.analysis.executables.ELFAnalyzer;
import org.opengrok.indexer.analysis.executables.JarAnalyzer;
import org.opengrok.indexer.analysis.executables.JavaClassAnalyzer;
import org.opengrok.indexer.analysis.perl.PerlAnalyzer;
import org.opengrok.indexer.analysis.plain.PlainAnalyzer;
import org.opengrok.indexer.analysis.plain.XMLAnalyzer;
import org.opengrok.indexer.analysis.sh.ShAnalyzer;
import org.opengrok.indexer.analysis.sh.ShAnalyzerFactory;
import static org.junit.Assert.*;

/**
 * Tests for the functionality provided by the AnalyzerGuru class.
 */
public class AnalyzerGuruTest {
    @Test
    public void testGetFileTypeDescriptions() {
        Map<String,String> map = AnalyzerGuru.getfileTypeDescriptions();
        Assert.assertTrue(map.size() > 0);
    }
    
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
                       0, '#', 0, ' ', (byte) 0xC2, (byte) 0xA9};
        ByteArrayInputStream in = new ByteArrayInputStream(doc);
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame("despite BOM as precise match,", PlainAnalyzer.class,
            fa.getClass());
    }

    @Test
    public void testUTF16LittleByteOrderMarkPlusCopyrightSymbol() throws Exception {
        byte[] doc = {(byte) 0xFF, (byte) 0xFE, // UTF-16BE BOM
                       '#', 0, ' ', 0, (byte) 0xA9, (byte) 0xC2};
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
        String s = File.separator;  // so test works on Unix and Windows
        String path = s+"path"+s+"to"+s+"Makefile";
        FileAnalyzerFactory faf = AnalyzerGuru.find(path);
        Class c = faf.getClass();
        assertSame(ShAnalyzerFactory.class, faf.getClass());
        faf = AnalyzerGuru.find("GNUMakefile");
        assertSame(ShAnalyzerFactory.class, faf.getClass());
    }
    
    /**
     * Test for obtaining a language analyzer's factory class.
     * This should not fail even if package names change.
     * The only assumptions made is that all the language analyzer 
     * and factory names follow the pattern:
     * 
     *  language + "Analyzer",  and 
     *  language + "AnalyzerFactory"
     */
    @Test
    public void getAnalyzerFactoryClass() {
        Class fc_forSh = AnalyzerGuru.getFactoryClass("Sh");
        Class fc_forShAnalyzer = AnalyzerGuru.getFactoryClass("ShAnalyzer");
        Class fc_simpleName = AnalyzerGuru.getFactoryClass("ShAnalyzerFactory");
        assertEquals(ShAnalyzerFactory.class, fc_forSh);
        assertEquals(ShAnalyzerFactory.class,fc_forShAnalyzer);
        assertEquals(ShAnalyzerFactory.class,fc_simpleName);
        
        Class fc = AnalyzerGuru.getFactoryClass("UnknownAnalyzerFactory");
        assertNull(fc);
    }

    @Test
    public void shouldNotThrowGettingCsprojOpening() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
            "analysis/a.csproj");
        assertNotNull("despite embedded a.csproj,", res);
        assertSame("despite normal a.csproj,", XMLAnalyzer.class,
            AnalyzerGuru.getAnalyzer(res, "dummy").getClass());
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

    @Test
    public void shouldMatchELFMagic() throws Exception {
        byte[] elfmt = {(byte)0x7F, 'E', 'L', 'F', (byte) 2, (byte) 2, (byte) 1,
            (byte) 0x06};
        ByteArrayInputStream in = new ByteArrayInputStream(elfmt);
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame("despite \\177ELF magic,", ELFAnalyzer.class,
            fa.getClass());
    }

    @Test
    public void shouldMatchJavaClassMagic() throws Exception {
        String oldMagic = "\312\376\272\276";      // cafebabe?
        String newMagic = new String(new byte[] {(byte) 0xCA, (byte) 0xFE,
            (byte) 0xBA, (byte) 0xBE});
        assertNotEquals("despite octal escape as unicode,", oldMagic, newMagic);

        // 0xCAFEBABE (4), minor (2), major (2)
        byte[] dotclass = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
            (byte) 0, (byte) 1, (byte) 0, (byte) 0x34};
        ByteArrayInputStream in = new ByteArrayInputStream(dotclass);
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame("despite 0xCAFEBABE magic,", JavaClassAnalyzer.class,
            fa.getClass());
    }

    @Test
    public void shouldMatchTroffMagic() throws Exception {
        byte[] mandoc = {' ', '\n', '.', '\"', '\n', '.', 'T', 'H',
            (byte) 0x20, '\n'};
        ByteArrayInputStream in = new ByteArrayInputStream(mandoc);
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame("despite .TH magic,", TroffAnalyzer.class,
            fa.getClass());
    }

    @Test
    public void shouldMatchMandocMagic() throws Exception {
        byte[] mandoc = {'\n', ' ', '.', '\"', '\n', '.', 'D', 'd',
            (byte) 0x20, '\n'};
        ByteArrayInputStream in = new ByteArrayInputStream(mandoc);
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame("despite .Dd magic,", MandocAnalyzer.class,
            fa.getClass());
    }
}
