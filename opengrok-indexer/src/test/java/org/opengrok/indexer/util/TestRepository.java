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
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A source repository to be used during a test.
 *
 * @author Trond Norbye
 */
public class TestRepository {

    private static final String URL_FILE_PROTOCOL = "file";
    private static final char JAR_PATH_DELIMITER = '!';

    private static final Map<Path, Path> renameMappings = new LinkedHashMap<>(Map.of(
            Path.of("bazaar", "bzr"),
            Path.of("bazaar", ".bzr"),

            Path.of("bitkeeper", "bk"),
            Path.of("bitkeeper", ".bk"),

            Path.of("mercurial", "hg"),
            Path.of("mercurial", ".hg"),

            Path.of("mercurial", "hgignore"),
            Path.of("mercurial", ".hgignore"),

            Path.of("git", "git"),
            Path.of("git", ".git"),

            Path.of("cvs_test", "cvsrepo", "CVS_dir"),
            Path.of("cvs_test", "cvsrepo", "CVS")
    ));

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

    public void create(@NotNull final URL url) throws IOException, URISyntaxException {
        createEmpty();
        if (url.getProtocol().equals(URL_FILE_PROTOCOL)) {
            copyDirectory(Path.of(url.toURI()), sourceRoot.toPath());
        } else {
            try (var fs = FileSystems.newFileSystem(url.toURI(), Map.of())) {
                var urlStr = url.toString();
                copyDirectory(fs.getPath(urlStr.substring(urlStr.indexOf(JAR_PATH_DELIMITER) + 1)),
                        sourceRoot.toPath());
            }
        }
    }

    /**
     * Assumes the destination directory exists.
     * @param src source directory
     * @param dest destination directory
     * @throws IOException on error
     */
    public void copyDirectory(Path src, Path dest) throws IOException {
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(sourceFile -> {
                if (sourceFile.equals(src)) {
                    return;
                }
                try {
                    Path destRelativePath = getDestinationRelativePath(src, sourceFile);
                    Path destPath = dest.resolve(destRelativePath);
                    if (Files.isDirectory(sourceFile)) {
                        if (!Files.exists(destPath)) {
                            Files.createDirectory(destPath);
                        }
                        return;
                    }
                    Files.copy(sourceFile, destPath, REPLACE_EXISTING, COPY_ATTRIBUTES);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private Path getDestinationRelativePath(Path sourceDirectory, Path sourceFile) {
        // possibly strip zip filesystem for the startsWith method to work
        var relativePath = Path.of(sourceDirectory.relativize(sourceFile).toString());
        for (var e : renameMappings.entrySet()) {
            if (relativePath.startsWith(e.getKey())) {
                if (relativePath.getNameCount() > e.getKey().getNameCount()) {
                    relativePath = relativePath.subpath(e.getKey().getNameCount(), relativePath.getNameCount());
                } else {
                    relativePath = Path.of("");
                }
                relativePath = e.getValue().resolve(relativePath);
                break;
            }
        }
        return relativePath;
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
        try {
            if (sourceRoot != null) {
                IOUtils.removeRecursive(sourceRoot.toPath());
            }
            if (externalRoot != null) {
                IOUtils.removeRecursive(externalRoot.toPath());
            }
            if (dataRoot != null) {
                IOUtils.removeRecursive(dataRoot.toPath());
            }
        } catch (IOException ignore) {
            // ignored
        }
    }

    /**
     * Deletes the directory tree of {@link #getDataRoot()}, and then recreates
     * the empty directory afterward.
     */
    public void purgeData() throws IOException {
        if (dataRoot != null) {
            IOUtils.removeRecursive(dataRoot.toPath());
            assertFalse(dataRoot.exists(), "dataRoot should not exist");
            assertTrue(dataRoot.mkdir(), "should recreate dataRoot");
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

            assertNotNull(inputBundle, "inputBundle should not be null");
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
