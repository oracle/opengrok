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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import org.junit.AfterClass;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.archive.BZip2Analyzer;
import org.opensolaris.opengrok.analysis.archive.GZIPAnalyzer;
import org.opensolaris.opengrok.history.RepositoryTest;
import org.opensolaris.opengrok.util.TestRepository;

/**
 * Represents a container for tests of {@link DigestedInputStream}.
 */
public class DigestedInputStreamTest {
    private static TestRepository repository;

    @BeforeClass
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(RepositoryTest.class.getResourceAsStream(
            "repositories.zip"));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        repository.destroy();
        repository = null;
    }

    @Test
    public void shouldDigestTextFileTwoWays() throws IOException {
        File sample = new File(repository.getSourceRoot(), "git/Makefile");
        assertTrue("git/Makefile exists", sample.exists());

        StreamSource src = StreamSource.fromFile(sample);
        assertNotNull("StreamSource.fromFile() result", src);

        // Digest the entire file at once.
        DigestedInputStream dis = src.getSHA256stream();
        assertNotNull("src.getSHA256stream() result", dis);
        byte[] digest1 = dis.digestAll();
        assertNotNull("dis.digestAll() result", digest1);

        // Digest the entire file implicitly while reading a stream.
        dis = src.getSHA256stream();
        while (dis.getStream().read() != -1) { /* noop */ }
        MessageDigest messageDigest = dis.getMessageDigest();
        assertNotNull("dis.getMessageDigest() result", messageDigest);
        byte[] digest2 = messageDigest.digest();

        assertArrayEquals("digestAll() v. read/digest()", digest1, digest2);
    }

    @Test
    public void shouldDigestGzipFileThreeWays() throws IOException {
        File sample = new File(repository.getSourceRoot(),
            "svn/archives/tarfile.tar.gz");
        assertTrue("svn/archives/tarfile.tar.gz exists", sample.exists());

        StreamSource src1 = StreamSource.fromFile(sample);
        assertNotNull("StreamSource.fromFile() result", src1);

        // Digest the entire file at once.
        DigestedInputStream dis = src1.getSHA256stream();
        assertNotNull("src.getSHA256stream() result", dis);
        byte[] digest1 = dis.digestAll();
        assertNotNull("dis.digestAll() result", digest1);

        // Digest the entire file implicitly while reading a raw stream.
        dis = src1.getSHA256stream();
        int rawcount = 0;
        while (dis.getStream().read() != -1) { ++rawcount; }
        MessageDigest messageDigest = dis.getMessageDigest();
        assertNotNull("dis.getMessageDigest() result", messageDigest);
        byte[] digest2 = messageDigest.digest();

        // Digest the entire file implicitly while reading a gunzip stream.
        StreamSource src2 = GZIPAnalyzer.wrap(src1);
        assertNotNull("GZIPAnalyzer.wrap() result", src2);
        dis = src2.getSHA256stream();
        int cookedcount = 0;
        while (dis.getStream().read() != -1) { ++cookedcount; }
        messageDigest = dis.getMessageDigest();
        assertNotNull("dis.getMessageDigest() result", messageDigest);
        byte[] digest3 = messageDigest.digest();

        assertArrayEquals("digestAll() v. read #1/digest()", digest1, digest2);
        assertArrayEquals("digestAll() v. read #2/digest()", digest1, digest3);
        assertTrue("some bytes read", rawcount > 0 && cookedcount > 0);
        assertNotEquals("raw byte# vs unzipped byte#", rawcount, cookedcount);
    }

    @Test
    public void shouldDigestBzip2FileThreeWays() throws IOException {
        File sample = new File(repository.getSourceRoot(),
            "svn/archives/tarfile.tar.bz2");
        assertTrue("svn/archives/tarfile.tar.bz2 exists", sample.exists());

        StreamSource src1 = StreamSource.fromFile(sample);
        assertNotNull("StreamSource.fromFile() result", src1);

        // Digest the entire file at once.
        DigestedInputStream dis = src1.getSHA256stream();
        assertNotNull("src.getSHA256stream() result", dis);
        byte[] digest1 = dis.digestAll();
        assertNotNull("dis.digestAll() result", digest1);

        // Digest the entire file implicitly while reading a raw stream.
        dis = src1.getSHA256stream();
        int rawcount = 0;
        while (dis.getStream().read() != -1) { ++rawcount; }
        MessageDigest messageDigest = dis.getMessageDigest();
        assertNotNull("dis.getMessageDigest() result", messageDigest);
        byte[] digest2 = messageDigest.digest();

        // Digest the entire file implicitly while reading a bzip2 stream.
        StreamSource src2 = BZip2Analyzer.wrap(src1);
        assertNotNull("BZip2Analyzer.wrap() result", src2);
        dis = src2.getSHA256stream();
        int cookedcount = 0;
        while (dis.getStream().read() != -1) { ++cookedcount; }
        messageDigest = dis.getMessageDigest();
        assertNotNull("dis.getMessageDigest() result", messageDigest);
        byte[] digest3 = messageDigest.digest();

        assertArrayEquals("digestAll() v. read #1/digest()", digest1, digest2);
        assertArrayEquals("digestAll() v. read #2/digest()", digest1, digest3);
        assertTrue("some bytes read", rawcount > 0 && cookedcount > 0);
        assertNotEquals("raw byte# vs unzipped byte#", rawcount, cookedcount);
    }
}
