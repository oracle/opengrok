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
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                // Try to delete the file anyway.
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
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
     * List files in the directory recursively when looking for files only
     * ending with suffix.
     *
     * @param root starting directory
     * @param suffix suffix for the files
     * @return recursively traversed list of files with given suffix
     */
    public static List<File> listFilesRec(File root, String suffix) {
        List<File> results = new ArrayList<>();
        List<File> files = listFiles(root);
        for (File f : files) {
            if (f.isDirectory() && f.canRead() && !f.getName().equals(".") && !f.getName().equals("..")) {
                results.addAll(listFilesRec(f, suffix));
            } else if (suffix != null && !suffix.isEmpty() && f.getName().endsWith(suffix)) {
                results.add(f);
            } else if (suffix == null || suffix.isEmpty()) {
                results.add(f);
            }
        }
        return results;
    }

    /**
     * List files in the directory.
     *
     * @param root starting directory
     * @return list of file with suffix
     */
    public static List<File> listFiles(File root) {
        return listFiles(root, null);
    }

    /**
     * List files in the directory when looking for files only ending with
     * suffix.
     *
     * @param root starting directory
     * @param suffix suffix for the files
     * @return list of file with suffix
     */
    public static List<File> listFiles(File root, String suffix) {
        File[] files = root.listFiles((dir, name) -> {
            if (suffix != null && !suffix.isEmpty()) {
                return name.endsWith(suffix);
            } else {
                return true;
            }
        });
        if (files == null) {
            return new ArrayList<>();
        }
        return Arrays.asList(files);
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
}
