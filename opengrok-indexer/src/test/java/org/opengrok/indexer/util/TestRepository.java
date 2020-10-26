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
 * Copyright (c) 2008, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.opengrok.indexer.configuration.RuntimeEnvironment;

/**
 * A source repository to be used during a test.
 *
 * @author Trond Norbye
 */
public class TestRepository {

    private final RuntimeEnvironment env;
    private File sourceRoot;
    private File dataRoot;
    private File externalRoot;

    public TestRepository() {
        env = RuntimeEnvironment.getInstance();
    }

    public void createEmpty() throws IOException {
        sourceRoot = Files.createTempDirectory("source").toFile();
        dataRoot = Files.createTempDirectory("data").toFile();
        env.setSourceRoot(sourceRoot.getAbsolutePath());
        env.setDataRoot(dataRoot.getAbsolutePath());
    }

    public void create(InputStream inputBundle) throws IOException {
        createEmpty();
        extractBundle(sourceRoot, inputBundle);
    }

    public void createExternal(InputStream inputBundle) throws IOException {
        createEmpty();
        externalRoot = Files.createTempDirectory("external").toFile();
        extractBundle(externalRoot, inputBundle);
    }

    public void destroy() {
        if (sourceRoot != null) {
            FileUtilities.removeDirs(sourceRoot);
        }
        if (externalRoot != null) {
            FileUtilities.removeDirs(externalRoot);
        }
        if (dataRoot != null) {
            FileUtilities.removeDirs(dataRoot);
        }
    }

    /**
     * Deletes the directory tree of {@link #getDataRoot()}, and then recreates
     * the empty directory afterward.
     */
    public void purgeData() {
        if (dataRoot != null) {
            assertTrue("should delete dataRoot", FileUtilities.removeDirs(dataRoot));
            assertFalse("dataRoot should not exist", dataRoot.exists());
            assertTrue("should recreate dataRoot", dataRoot.mkdir());
        }
    }

    public String getSourceRoot() {
        return sourceRoot.getAbsolutePath();
    }

    public String getDataRoot() {
        return dataRoot.getAbsolutePath();
    }

    public String getExternalRoot() {
        return externalRoot == null ? null : externalRoot.getAbsolutePath();
    }

    private static final String DUMMY_FILENAME = "dummy.txt";

    public File addDummyFile(String project) throws IOException {
        File dummy = new File(getSourceRoot() + File.separator + project +
            File.separator + DUMMY_FILENAME);
        if (!dummy.exists()) {
            dummy.createNewFile();
        }
        return dummy;
    }

    public void addDummyFile(String project, String contents) throws IOException {
        File dummy = addDummyFile(project);
        Files.write(dummy.toPath(), contents.getBytes());
    }

    public void removeDummyFile(String project) {
        File dummy = new File(getSourceRoot() + File.separator + project +
            File.separator + DUMMY_FILENAME);
        dummy.delete();
    }

    /**
     * Add an ad-hoc file of a specified name with contents from the specified
     * stream.
     * @param filename a required instance
     * @param in a required instance
     * @param project an optional project name
     * @return file object
     * @throws IOException I/O exception
     */
    public File addAdhocFile(String filename, InputStream in, String project)
            throws IOException {

        String projsep = project != null ? File.separator + project : "";
        File adhoc = new File(getSourceRoot() + projsep + File.separator +
            filename);

        byte[] buf = new byte[8192];
        try (FileOutputStream out = new FileOutputStream(adhoc)) {
            int r;
            if ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
        }
        return adhoc;
    }

    private void extractBundle(File target, InputStream inputBundle) throws IOException {
        File sourceBundle = null;
        try {
            sourceBundle = File.createTempFile("srcbundle", ".zip");
            if (sourceBundle.exists()) {
                assertTrue(sourceBundle.delete());
            }

            assertNotNull("inputBundle should not be null", inputBundle);
            FileOutputStream out = new FileOutputStream(sourceBundle);
            FileUtilities.copyFile(inputBundle, out);
            out.close();
            FileUtilities.extractArchive(sourceBundle, target);
        } finally {
            if (sourceBundle != null) {
                sourceBundle.delete();
            }
        }
    }
}
