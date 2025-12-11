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
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2011, Trond Norbye.
 * Portions Copyright (c) 2017, 2021, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * A small utility class to provide common functionality related to
 * IO so that we don't need to duplicate the logic all over the place.
 *
 * @author Trond Norbye &lt;trond.norbye@gmail.com&gt;
 */
public final class IOUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(IOUtils.class);

    // private to enforce static
    private IOUtils() {
    }

    /**
     * If {@code c} is not null, tries to {@code close}, catching and logging
     * any {@link IOException}.
     * @param c null or a defined instance
     */
    public static void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to close resource", e);
            }
        }
    }

    /**
     * Delete directory recursively. This method does not follow symlinks.
     * @param path directory to delete
     * @throws IOException if any read error
     */
    public static void removeRecursive(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult visitFileFailed(Path file, @NotNull IOException exc) throws IOException {
                // Try to delete the file anyway.
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc == null) {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                } else {
                    // Directory traversal failed.
                    throw exc;
                }
            }
        });
    }

    /**
     * List files in the directory recursively when looking for regular files ending with suffix.
     * If the suffix is {@code null}, add all regular files.
     *
     * @param root starting directory
     * @param suffix suffix for the files, can be {@code null}
     * @return recursively traversed list of files with given suffix
     */
    public static List<File> listFilesRecursively(@NotNull File root, @Nullable String suffix) throws IOException {
        return listFilesRecursively(root, suffix, Integer.MAX_VALUE);
    }

    /**
     * List files in the directory recursively when looking for regular files ending with suffix.
     * If the suffix is {@code null}, add all regular files.
     *
     * @param root starting directory
     * @param suffix suffix for the files, can be {@code null}
     * @param maxDepth maximum recursion depth
     * @return recursively traversed list of files with given suffix
     */
    public static List<File> listFilesRecursively(@NotNull File root, @Nullable String suffix, int maxDepth) throws IOException {
        class SuffixFileCollector extends SimpleFileVisitor<Path> {
            private final String suffix;
            private final List<File> collectedFiles = new ArrayList<>();

            SuffixFileCollector(String suffix) {
                this.suffix = suffix;
            }

            public List<File> getCollectedFiles() {
                return collectedFiles;
            }

            @Override
            public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }

                if (suffix == null) {
                    collectedFiles.add(file.toFile());
                } else {
                    if (file.getFileName().toString().endsWith(suffix)) {
                        collectedFiles.add(file.toFile());
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        }

        SuffixFileCollector collector = new SuffixFileCollector(suffix);
        Files.walkFileTree(root.toPath(), EnumSet.noneOf(FileVisitOption.class), maxDepth, collector);
        return collector.getCollectedFiles();
    }

    /**
     * List files in the directory.
     *
     * @param root directory
     * @return list of files in the directory
     */
    public static List<File> listFiles(File root) throws IOException {
        return listFiles(root, null);
    }

    /**
     * List files in the directory when looking for files only ending with given suffix.
     * Does not descend into subdirectories.
     *
     * @param root directory
     * @param suffix suffix for the files
     * @return list of file with suffix
     */
    public static List<File> listFiles(@NotNull File root, @Nullable String suffix) throws IOException {
        return listFilesRecursively(root, suffix, 1);
    }

    /**
     * Create BOM stripped reader from the stream.
     * Charset of the reader is set to UTF-8, UTF-16 or system's default.
     * @param stream input stream
     * @return reader for the stream without BOM
     * @throws IOException if I/O exception occurred
     */
    public static Reader createBOMStrippedReader(InputStream stream) throws IOException {
        return createBOMStrippedReader(stream, Charset.defaultCharset().name());
    }

    /**
     * Create BOM stripped reader from the stream.
     * Charset of the reader is set to UTF-8, UTF-16 or default.
     * @param stream input stream
     * @param defaultCharset default charset
     * @return reader for the stream without BOM
     * @throws IOException if I/O exception occurred
     */
    public static Reader createBOMStrippedReader(InputStream stream, String defaultCharset) throws IOException {
        InputStream in = stream.markSupported() ?
                stream : new BufferedInputStream(stream);

        String charset = null;

        in.mark(3);

        byte[] head = new byte[3];
        int br = in.read(head, 0, 3);

        if (br >= 2
                && (head[0] == (byte) 0xFE && head[1] == (byte) 0xFF)
                || (head[0] == (byte) 0xFF && head[1] == (byte) 0xFE)) {
            charset = StandardCharsets.UTF_16.name();
            in.reset();
        } else if (br >= 3 && head[0] == (byte) 0xEF && head[1] == (byte) 0xBB
                && head[2] == (byte) 0xBF) {
            // InputStreamReader does not properly discard BOM on UTF8 streams,
            // so don't reset the stream.
            charset = StandardCharsets.UTF_8.name();
        }

        if (charset == null) {
            in.reset();
            charset = defaultCharset;
        }

        return new InputStreamReader(in, charset);
    }

    /**
     * Byte-order markers.
     */
    private static final Map<String, byte[]> BOMS = Map.of(
            StandardCharsets.UTF_8.name(), utf8Bom(),
            StandardCharsets.UTF_16BE.name(), utf16BeBom(),
            StandardCharsets.UTF_16LE.name(), utf16LeBom()
    );

    /**
     * Gets a new array containing the UTF-8 BOM.
     */
    public static byte[] utf8Bom() {
        return new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    }

    /**
     * Gets a new array containing the UTF-16BE BOM (Big-Endian).
     */
    public static byte[] utf16BeBom() {
        return new byte[]{(byte) 0xFE, (byte) 0xFF};
    }

    /**
     * Gets a new array containing the UTF-16LE BOM (Little-Endian).
     */
    public static byte[] utf16LeBom() {
        return new byte[]{(byte) 0xFF, (byte) 0xFE};
    }

    /**
     * Gets a value indicating a UTF encoding if the array starts with a
     * known byte sequence.
     *
     * @param sig a sequence of bytes to inspect for a BOM
     * @return null if no BOM was identified; otherwise a defined charset name
     */
    public static String findBOMEncoding(byte[] sig) {
        for (Map.Entry<String, byte[]> entry : BOMS.entrySet()) {
            String encoding = entry.getKey();
            byte[] bom = entry.getValue();
            if (sig.length > bom.length) {
                int i = 0;
                while (i < bom.length && sig[i] == bom[i]) {
                    i++;
                }
                if (i == bom.length) {
                    return encoding;
                }
            }
        }
        return null;
    }

    /**
     * Gets a value indicating the number of UTF BOM bytes at the start of an
     * array.
     *
     * @param sig a sequence of bytes to inspect for a BOM
     * @return 0 if the array doesn't start with a BOM; otherwise the number of
     * BOM bytes
     */
    public static int skipForBOM(byte[] sig) {
        String encoding = findBOMEncoding(sig);
        if (encoding != null) {
            byte[] bom = BOMS.get(encoding);
            return bom.length;
        }
        return 0;
    }

    /**
     * Get the contents of a file or empty string if the file cannot be read.
     * @param file file object
     * @return string with the file contents
     */
    public static String getFileContent(File file) {
        if (file == null || !file.canRead()) {
            return "";
        }
        try {
            return Files.readString(file.toPath(), Charset.defaultCharset());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "failed to read file: {0}", e.getMessage());
        }
        return "";
    }

    /**
     * Create temporary directory with permissions restricted to the owner.
     * @param isDirectory whether this is a file or directory
     * @param prefix prefix for the temporary directory name
     * @param suffix optional suffix, can be {@code null} for directories
     * @return File object
     * @throws IOException on I/O error or failure to set the permissions
     */
    public static File createTemporaryFileOrDirectory(boolean isDirectory, String prefix, String suffix) throws IOException {
        File tmp;
        if (SystemUtils.IS_OS_UNIX) {
            FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.
                    asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            if (isDirectory) {
                tmp = Files.createTempDirectory(prefix, attr).toFile();
            } else {
                tmp = Files.createTempFile(prefix, suffix, attr).toFile();
            }
        } else {
            if (isDirectory) {
                tmp = Files.createTempDirectory(prefix).toFile();
            } else {
                tmp = Files.createTempFile(prefix, suffix).toFile();
            }
            if (!tmp.setReadable(true, true)) {
                throw new IOException("unable to set read permissions for '" + tmp.getAbsolutePath() + "'");
            }
            if (!tmp.canWrite() && !tmp.setWritable(true, true)) {
                throw new IOException("unable to set write permissions for '" + tmp.getAbsolutePath() + "'");
            }
            if (!tmp.setExecutable(true, true)) {
                throw new IOException("unable to set executable permissions for '" + tmp.getAbsolutePath() + "'");
            }
        }
        return tmp;
    }
}
