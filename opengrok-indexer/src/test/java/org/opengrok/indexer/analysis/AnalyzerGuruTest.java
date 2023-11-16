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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the functionality provided by the AnalyzerGuru class.
 */
class AnalyzerGuruTest {

    @Test
    void testGetFileTypeDescriptions() {
        Map<String, String> map = AnalyzerGuru.getfileTypeDescriptions();
        assertFalse(map.isEmpty());
    }

    /**
     * Test that we get the correct analyzer if the file name exactly matches a
     * known extension.
     */
    @Test
    void testFileNameSameAsExtension() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "#!/bin/sh\nexec /usr/bin/zip \"$@\"\n".getBytes(StandardCharsets.US_ASCII));
        String file = "/dummy/path/to/source/zip";
        AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, file);
        assertSame(ShAnalyzer.class, fa.getClass());
    }

    @Test
    void testUTF8ByteOrderMark() throws Exception {
        byte[] xml = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, // UTF-8 BOM
                '<', '?', 'x', 'm', 'l', ' ',
                'v', 'e', 'r', 's', 'i', 'o', 'n', '=',
                '"', '1', '.', '0', '"', '?', '>'};
        ByteArrayInputStream in = new ByteArrayInputStream(xml);
        AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame(XMLAnalyzer.class, fa.getClass());
    }

    @Test
    void testUTF8ByteOrderMarkPlusCopyrightSymbol() throws Exception {
        byte[] doc = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, // UTF-8 BOM
                '/', '/', ' ', (byte) 0xC2, (byte) 0xA9};
        ByteArrayInputStream in = new ByteArrayInputStream(doc);
        AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame(PlainAnalyzer.class, fa.getClass(), "despite BOM as precise match,");
    }

    @Test
    void testUTF8ByteOrderMarkPlainFile() throws Exception {
        byte[] bytes = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, // UTF-8 BOM
                'h', 'e', 'l', 'l', 'o', ' ',
                'w', 'o', 'r', 'l', 'd'};

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame(PlainAnalyzer.class, fa.getClass());
    }

    @Test
    void testUTF16BigByteOrderMarkPlusCopyrightSymbol() throws Exception {
        byte[] doc = {(byte) 0xFE, (byte) 0xFF, // UTF-16BE BOM
                0, '#', 0, ' ', (byte) 0xC2, (byte) 0xA9};
        ByteArrayInputStream in = new ByteArrayInputStream(doc);
        AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame(PlainAnalyzer.class, fa.getClass(), "despite BOM as precise match,");
    }

    @Test
    void testUTF16LittleByteOrderMarkPlusCopyrightSymbol() throws Exception {
        byte[] doc = {(byte) 0xFF, (byte) 0xFE, // UTF-16BE BOM
                '#', 0, ' ', 0, (byte) 0xA9, (byte) 0xC2};
        ByteArrayInputStream in = new ByteArrayInputStream(doc);
        AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame(PlainAnalyzer.class, fa.getClass(), "despite BOM as precise match,");
    }

    @Test
    void addExtension() throws Exception {
        // should not find analyzer for this unlikely extension
        assertNull(AnalyzerGuru.find("file.unlikely_extension"));

        AnalyzerFactory
                faf = AnalyzerGuru.findFactory(ShAnalyzerFactory.class.getName());
        // should be the same factory as the built-in analyzer for sh scripts
        assertSame(AnalyzerGuru.find("myscript.sh"), faf);

        // add an analyzer for the extension and see that it is picked up
        AnalyzerGuru.addExtension("UNLIKELY_EXTENSION", faf);
        assertSame(ShAnalyzerFactory.class, AnalyzerGuru.find("file.unlikely_extension").getClass());

        // remove the mapping and verify that it is gone
        AnalyzerGuru.addExtension("UNLIKELY_EXTENSION", null);
        assertNull(AnalyzerGuru.find("file.unlikely_extension"));
    }

    @Test
    void addPrefix() throws Exception {
        // should not find analyzer for this unlikely extension
        assertNull(AnalyzerGuru.find("unlikely_prefix.foo"));

        AnalyzerFactory
                faf = AnalyzerGuru.findFactory(ShAnalyzerFactory.class.getName());
        // should be the same factory as the built-in analyzer for sh scripts
        assertSame(AnalyzerGuru.find("myscript.sh"), faf);

        // add an analyzer for the prefix and see that it is picked up
        AnalyzerGuru.addPrefix("UNLIKELY_PREFIX", faf);
        assertSame(ShAnalyzerFactory.class, AnalyzerGuru.find("unlikely_prefix.foo").getClass());

        // remove the mapping and verify that it is gone
        AnalyzerGuru.addPrefix("UNLIKELY_PREFIX", null);
        assertNull(AnalyzerGuru.find("unlikely_prefix.foo"));
    }

    @Test
    void testZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("dummy"));
        zos.closeEntry();
        zos.close();
        InputStream in = new ByteArrayInputStream(baos.toByteArray());
        AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "dummy");
        assertSame(ZipAnalyzer.class, fa.getClass());
    }

    @Test
    void testJar() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JarOutputStream jos = new JarOutputStream(baos);
        jos.putNextEntry(new JarEntry("dummy"));
        jos.closeEntry();
        jos.close();
        InputStream in = new ByteArrayInputStream(baos.toByteArray());
        AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "dummy");
        assertSame(JarAnalyzer.class, fa.getClass());
    }

    @Test
    void testPlainText() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "This is a plain text file.".getBytes(StandardCharsets.US_ASCII));
        assertSame(PlainAnalyzer.class, AnalyzerGuru.getAnalyzer(in, "dummy").getClass());
    }

    @Test
    void rfe2969() {
        AnalyzerFactory faf = AnalyzerGuru.find("foo.hxx");
        assertNotNull(faf);
        assertSame(CxxAnalyzerFactory.class, faf.getClass());
    }

    @Test
    void rfe3401() {
        AnalyzerFactory f1 = AnalyzerGuru.find("main.c");
        assertNotNull(f1);
        AnalyzerFactory f2 = AnalyzerGuru.find("main.cc");
        assertNotNull(f2);
        assertNotSame(f1.getClass(), f2.getClass());
    }

    /**
     * Test that matching of full names works. Bug #859.
     */
    @Test
    void matchesFullName() {
        String s = File.separator;  // so test works on Unix and Windows
        String path = s + "path" + s + "to" + s + "Makefile";
        AnalyzerFactory faf = AnalyzerGuru.find(path);
        assertSame(ShAnalyzerFactory.class, faf.getClass());
        faf = AnalyzerGuru.find("GNUMakefile");
        assertSame(ShAnalyzerFactory.class, faf.getClass());
    }

    /**
     * Test for obtaining a language analyzer's factory class.
     * This should not fail even if package names change.
     * The only assumptions made is that all the language analyzer
     * and factory names follow the pattern:
     * <p>
     * language + "Analyzer",  and
     * language + "AnalyzerFactory"
     */
    @Test
    void getAnalyzerFactoryClass() {
        Class<?> fcForSh = AnalyzerGuru.getFactoryClass("Sh");
        Class<?> fcForShAnalyzer = AnalyzerGuru.getFactoryClass("ShAnalyzer");
        Class<?> fcSimpleName = AnalyzerGuru.getFactoryClass("ShAnalyzerFactory");
        assertEquals(ShAnalyzerFactory.class, fcForSh);
        assertEquals(ShAnalyzerFactory.class, fcForShAnalyzer);
        assertEquals(ShAnalyzerFactory.class, fcSimpleName);

        Class<?> fc = AnalyzerGuru.getFactoryClass("UnknownAnalyzerFactory");
        assertNull(fc);
    }

    @Test
    void shouldNotThrowGettingCsprojOpening() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream("analysis/a.csproj");
        assertNotNull(res, "despite embedded a.csproj,");
        assertSame(XMLAnalyzer.class, AnalyzerGuru.getAnalyzer(res, "dummy").getClass(), "despite normal a.csproj,");
    }

    @Test
    void shouldMatchPerlHashbang() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "#!/usr/bin/perl -w".getBytes(StandardCharsets.US_ASCII));
        assertSame(PerlAnalyzer.class, AnalyzerGuru.getAnalyzer(in, "dummy").getClass(), "despite Perl hashbang,");
    }

    @Test
    void shouldMatchPerlHashbangSpaced() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "\n\t #!  /usr/bin/perl -w".getBytes(StandardCharsets.US_ASCII));
        assertSame(PerlAnalyzer.class, AnalyzerGuru.getAnalyzer(in, "dummy").getClass(), "despite Perl hashbang,");
    }

    @Test
    void shouldMatchEnvPerlHashbang() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "#!/usr/bin/env perl -w".getBytes(StandardCharsets.US_ASCII));
        assertSame(PerlAnalyzer.class, AnalyzerGuru.getAnalyzer(in, "dummy").getClass(), "despite env hashbang with perl,");
    }

    @Test
    void shouldMatchEnvPerlHashbangSpaced() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "\n\t #!  /usr/bin/env\t perl -w".getBytes(StandardCharsets.US_ASCII));
        assertSame(PerlAnalyzer.class, AnalyzerGuru.getAnalyzer(in, "dummy").getClass(),
                "despite env hashbang with perl,");
    }

    @Test
    void shouldNotMatchEnvLFPerlHashbang() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "#!/usr/bin/env\nperl".getBytes(StandardCharsets.US_ASCII));
        assertNotSame(PerlAnalyzer.class, AnalyzerGuru.getAnalyzer(in, "dummy").getClass(), "despite env hashbang LF,");
    }

    @Test
    void shouldMatchELFMagic() throws Exception {
        byte[] elfmt = {(byte) 0x7F, 'E', 'L', 'F', (byte) 2, (byte) 2, (byte) 1,
                (byte) 0x06};
        ByteArrayInputStream in = new ByteArrayInputStream(elfmt);
        AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame(ELFAnalyzer.class, fa.getClass(), "despite \\177ELF magic,");
    }

    @Test
    void shouldMatchJavaClassMagic() throws Exception {
        String oldMagic = "\312\376\272\276";      // cafebabe?
        String newMagic = new String(new byte[] {(byte) 0xCA, (byte) 0xFE,
                (byte) 0xBA, (byte) 0xBE}, StandardCharsets.UTF_8);
        assertNotEquals(oldMagic, newMagic, "despite octal string, escape it as unicode,");

        // 0xCAFEBABE (4), minor (2), major (2)
        byte[] dotclass = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                (byte) 0, (byte) 1, (byte) 0, (byte) 0x34};
        ByteArrayInputStream in = new ByteArrayInputStream(dotclass);
        AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame(JavaClassAnalyzer.class, fa.getClass(), "despite 0xCAFEBABE magic,");
    }

    @Test
    void shouldMatchTroffMagic() throws Exception {
        byte[] mandoc = {' ', '\n', '.', '\"', '\n', '.', 'T', 'H', (byte) 0x20, '\n'};
        ByteArrayInputStream in = new ByteArrayInputStream(mandoc);
        AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame(TroffAnalyzer.class, fa.getClass(), "despite .TH magic,");
    }

    @Test
    void shouldMatchMandocMagic() throws Exception {
        byte[] mandoc = {'\n', ' ', '.', '\"', '\n', '.', 'D', 'd', (byte) 0x20, '\n'};
        ByteArrayInputStream in = new ByteArrayInputStream(mandoc);
        AbstractAnalyzer fa = AnalyzerGuru.getAnalyzer(in, "/dummy/file");
        assertSame(MandocAnalyzer.class, fa.getClass(), "despite .Dd magic,");
    }
}
