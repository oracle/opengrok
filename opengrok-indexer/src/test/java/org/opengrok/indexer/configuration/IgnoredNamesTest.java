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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.configuration;

import java.beans.ExceptionListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.c.CAnalyzerFactoryTest;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author Trond Norbye
 */
public class IgnoredNamesTest {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();
    private static TestRepository repository;

    @BeforeAll
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(CAnalyzerFactoryTest.class.getResourceAsStream(
                "/org/opengrok/indexer/index/source.zip"));

        // Populate ignored lists with repository specific entries.
        RepositoryFactory.initializeIgnoredNames(RuntimeEnvironment.getInstance());
    }

    /**
     * Check that RepositoryFactory added repository specific entries to
     * IgnoredNames.
     */
    @Test
    public void testIgnoredSpecialPatterns() {
        IgnoredNames instance = env.getIgnoredNames();

        /* Test handling of special directories. */
        assertTrue(instance.ignore("usr/src/.git"));
        assertFalse(instance.ignore("usr/src/.git/foo"));
        assertFalse(instance.ignore("usr/src/foo.git"));
    }

    @Test
    public void testAbsolutePathCheckWithPattern() {
        IgnoredNames instance = new IgnoredNames();
        String currentDir = new File("").getAbsolutePath();
        String currentDirName = currentDir.substring(currentDir.lastIndexOf('/') + 1);

        // Absolute path should not be matched against filenames.
        instance.add("*" + currentDirName + "*");
        assertFalse(instance.ignore("foo"));

        // Filename matching should still work.
        instance.add("*foo*");
        assertTrue(instance.ignore("foo"));
    }

    @Test
    public void testIgnoredPatterns() {
        IgnoredNames instance = new IgnoredNames();

        List<String> names = instance.getItems();
        assertNotNull(names);

        /* self-test */
        for (String name : names) {
            assertTrue(instance.ignore(name));
        }

        /* Make sure common paths are not ignored by default. */
        assertFalse(instance.ignore("usr/src/foo/bin"));
        assertFalse(instance.ignore("usr/src/foo/bin/bar.ksh"));
        assertFalse(instance.ignore("usr/src/bar/obj"));
        assertFalse(instance.ignore("usr/src/bar/obj/foo.ksh"));
        assertFalse(instance.ignore("usr/src/foo/bar/usr.lib/main.c"));
        assertFalse(instance.ignore("usr/src/foo/bar/usr.lib"));

        /* cumulative test */
        names = new ArrayList<>();
        names.add("*.o");

        instance.setItems(names);
        names = instance.getItems();
        assertEquals(1, names.size());

        assertTrue(instance.ignore("foo.o"));
        assertFalse(instance.ignore("foo"));
        assertTrue(instance.ignore(".o"));
        assertFalse(instance.ignore("foo.oo"));

        instance.add("f:Makefile");
        names = instance.getItems();
        assertEquals(2, names.size());
        assertTrue(instance.ignore(new File(repository.getSourceRoot()
                + "/c/Makefile")));

        assertFalse(instance.ignore("main.c"));

        instance.add("o*o?.a?c*");
        assertTrue(instance.ignore("opengrok.abc"));
        assertTrue(instance.ignore("opengrok.abcd"));
        assertFalse(instance.ignore("opengrok.ac"));
        assertFalse(instance.ignore("grok.abcd"));

        instance.add("d:haskell");
        assertTrue(instance.ignore(new File(repository.getSourceRoot()
                + "/haskell")));

        instance.clear();
        names = instance.getItems();
        assertEquals(0, names.size());
    }

    /**
     * Make sure that encoding and decoding IgnoredNames object is 1:1 operation.
     */
    @Test
    public void testEncodeDecode() throws IOException {
        IgnoredNames in = new IgnoredNames();
        // Add file and directory to list of ignored items.
        in.add("f:foo.txt");
        in.add("d:bar");

        // Create an exception listener to detect errors while encoding and decoding
        final LinkedList<Exception> exceptions = new LinkedList<>();
        ExceptionListener listener = exceptions::addLast;

        // Actually create the file and directory for much better test coverage.
        File tmpdir = Files.createTempDirectory("ignoredNames").toFile();
        File foo = new File(tmpdir, "foo.txt");
        foo.createNewFile();
        assertTrue(foo.isFile());
        File bar = new File(tmpdir, "bar");
        bar.mkdir();
        assertTrue(bar.isDirectory());

        // Store the IgnoredNames object as XML file.
        File testXML = new File(tmpdir, "Test.xml");
        XMLEncoder e = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(testXML)));
        e.setExceptionListener(listener);
        e.writeObject(in);
        e.close();

        // Restore the IgnoredNames object from XML file.
        XMLDecoder d = new XMLDecoder(new FileInputStream(testXML));
        IgnoredNames in2 = (IgnoredNames) d.readObject();
        d.close();

        // Verify that the XML encoding/decoding did not fail.
        if (!exceptions.isEmpty()) {
            // Can only chain one of the exceptions. Take the first one.
            throw new AssertionError("Got " + exceptions.size() + " exception(s)", exceptions.getFirst());
        }

        // Make sure the complete list of items is equal after decoding.
        // This will is a simple casual test that cannot verify that sub-classes
        // are intact. For that there are the following tests.
        assertTrue(in.getItems().containsAll(in2.getItems()));

        // Use the restored object to test the matching of file and directory.
        assertTrue(in2.ignore("foo.txt"));
        assertTrue(in2.ignore("bar"));
        assertTrue(in2.ignore(foo));
        assertTrue(in2.ignore(bar));

        // Cleanup.
        IOUtils.removeRecursive(tmpdir.toPath());
    }
}
