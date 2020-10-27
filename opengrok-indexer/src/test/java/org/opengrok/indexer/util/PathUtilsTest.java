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
 * Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.UnixPresent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Represents a container for tests of {@link PathUtils}.
 */
public class PathUtilsTest {

    private final List<File> tempDirs = new ArrayList<>();

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    private String relativeToCanonical(String path, String canonical) throws IOException {
        return PathUtils.getRelativeToCanonical(Paths.get(path), Paths.get(canonical));
    }

    private String relativeToCanonical(String path, String canonical, Set<String> allowedSymlinks,
        Set<String> canonicalRoots)
        throws IOException, ForbiddenSymlinkException {
        return PathUtils.getRelativeToCanonical(Paths.get(path), Paths.get(canonical), allowedSymlinks, canonicalRoots);
    }

    @After
    public void tearDown() {
        try {
            tempDirs.forEach((tempDir) -> {
                try {
                    IOUtils.removeRecursive(tempDir.toPath());
                } catch (IOException e) {
                    // ignore
                }
            });
        } finally {
            tempDirs.clear();
        }
    }

    @Test
    public void shouldHandleSameInputs() throws IOException {
        final String USR_BIN = Paths.get("/usr/bin").toString();
        String rel = relativeToCanonical(USR_BIN, USR_BIN);
        Assert.assertEquals(USR_BIN + " rel to itself", "", rel);
    }

    @Test
    public void shouldHandleEffectivelySameInputs() throws IOException {
        String USR_BIN = Paths.get(Paths.get("/usr/bin").toUri()).toString();
        String rel = relativeToCanonical(USR_BIN + File.separator, USR_BIN);
        Assert.assertEquals(USR_BIN + " rel to ~itself", "", rel);
    }

    @Test
    @ConditionalRun(UnixPresent.class)
    public void shouldHandleLinksOfArbitraryDepthWithValidation()
        throws IOException, ForbiddenSymlinkException {
        // Create real directories
        File sourceRoot = createTemporaryDirectory("srcroot");
        assertTrue("sourceRoot.isDirectory()", sourceRoot.isDirectory());

        File realDir1 = createTemporaryDirectory("realdir1");
        assertTrue("realDir1.isDirectory()", realDir1.isDirectory());
        File realDir1b = new File(realDir1, "b");
        realDir1b.mkdir();
        assertTrue("realDir1b.isDirectory()", realDir1b.isDirectory());

        File realDir2 = createTemporaryDirectory("realdir2");
        assertTrue("realDir2.isDirectory()", realDir2.isDirectory());

        // Create symlink #1 underneath source root (srcroot/symlink1 -> realdir1)
        final String SYMLINK1 = "symlink1";
        File symlink1 = new File(sourceRoot, SYMLINK1);
        Files.createSymbolicLink(Paths.get(symlink1.getPath()),
            Paths.get(realDir1.getPath()));
        assertTrue("symlink1.exists()", symlink1.exists());

        // Create symlink #2 underneath realdir1/b (realdir1/b/symlink2 -> realdir2)
        final String SYMLINK2 = "symlink2";
        File symlink2 = new File(realDir1b, SYMLINK2);
        Files.createSymbolicLink(Paths.get(symlink2.getPath()),
            Paths.get(realDir2.getPath()));
        assertTrue("symlink2.exists()", symlink2.exists());

        // Assert symbolic path srcroot/symlink1/b/symlink2
        Path sympath = Paths.get(sourceRoot.getPath(), SYMLINK1, "b",
            SYMLINK2);
        assertTrue("2-link path exists", Files.exists(sympath));

        // Test v. realDir1 canonical
        String realDir1Canon = realDir1.getCanonicalPath();
        String rel = relativeToCanonical(sympath.toString(), realDir1Canon);
        assertEquals("because links aren't validated", "b/" + SYMLINK2, rel);

        // Test v. realDir1 canonical with validation and no allowed links
        Set<String> allowedSymLinks = new HashSet<>();
        ForbiddenSymlinkException expex = null;
        try {
            relativeToCanonical(sympath.toString(), realDir1Canon,
                    allowedSymLinks, null);
        } catch (ForbiddenSymlinkException e) {
            expex = e;
        }
        Assert.assertNotNull("because no links allowed, arg1 is returned fully",
            expex);

        // Test v. realDir1 canonical with validation and an allowed link
        allowedSymLinks.add(symlink2.getPath());
        rel = relativeToCanonical(sympath.toString(),
                realDir1Canon, allowedSymLinks, null);
        assertEquals("because link is OKed", "b/" + SYMLINK2, rel);
    }

    @Test
    @ConditionalRun(UnixPresent.class)
    public void shouldHandleLinksToCanonicalChildrenOfAllowedLinks()
            throws IOException, ForbiddenSymlinkException {
        // Create real directories
        File sourceRoot = createTemporaryDirectory("srcroot");
        assertTrue(sourceRoot + " should be a dir", sourceRoot.isDirectory());

        File realDir1 = createTemporaryDirectory("realdir1");
        assertTrue(realDir1 + " should be a dir", realDir1.isDirectory());

        File realDir1b = new File(realDir1, "b");
        assertTrue(realDir1b + " should be created", realDir1b.mkdir());

        // Create symlink #1 to realdir1/ in source root.
        final String SYMLINK1 = "symlink1";
        File symlink1 = new File(sourceRoot, SYMLINK1);
        Files.createSymbolicLink(symlink1.toPath(), realDir1.toPath());
        assertTrue(symlink1 + " should exist", symlink1.exists());

        // Create symlink #2 to realdir1/b in source root.
        final String SYMLINK2 = "symlink2";
        File symlink2 = new File(realDir1b, SYMLINK2);
        Files.createSymbolicLink(symlink2.toPath(), realDir1b.toPath());
        assertTrue(symlink2 + " should exist", symlink2.exists());

        // Test symlink2 v. realDir1 canonical with validation and an allowed symlink1
        Set<String> allowedSymLinks = new HashSet<>();
        allowedSymLinks.add(symlink1.getPath());

        String realDir1Canon = realDir1.getCanonicalPath();
        String rel = relativeToCanonical(symlink2.toString(),
                realDir1Canon, allowedSymLinks, null);
        assertEquals("symlink2 should be allowed implicitly as a canonical child of symlink1",
                "b", rel);
    }

    @Ignore("macOS has /var symlink, and I also made a second link, `myhome'.")
    @Test
    public void shouldResolvePrivateVarOnMacOS() throws IOException {
        final String MY_VAR_FOLDERS =
            "/var/folders/58/546k9lk08xl56t0059bln0_h0000gp/T/tilde/Documents";
        final String EXPECTED_REL = MY_VAR_FOLDERS.substring("/var/".length());
        String rel = relativeToCanonical(MY_VAR_FOLDERS,
            "/private/var");
        Assert.assertEquals("/var/run rel to /private/var", EXPECTED_REL, rel);
    }

    @Ignore("For ad-hoc testing with \\ in paths.")
    @Test
    public void mightResolveBackslashesToo() throws IOException {
        final String MY_VAR_FOLDERS =
            "\\var\\folders\\58\\546k9lk08xl56t0059bln0_h0000gp\\T";
        final String EXPECTED_REL = MY_VAR_FOLDERS.substring("/var/".length()).
            replace('\\', '/');
        String rel = relativeToCanonical(MY_VAR_FOLDERS,
            "/private/var");
        Assert.assertEquals("/var/run rel to /private/var", EXPECTED_REL, rel);
    }

    private File createTemporaryDirectory(String name) throws IOException {
        File f = Files.createTempDirectory(name).toFile();
        tempDirs.add(f);
        return f;
    }
}
