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
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2011 Trond Norbye 
 */
package org.opensolaris.opengrok.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.logger.LoggerFactory;

/**
 * A small utility class to provide common functionality related to
 * IO so that we don't need to duplicate the logic all over the place.
 * 
 * @author Trond Norbye &lt;trond.norbye@gmail.com&gt;
 */
public final class IOUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(IOUtils.class);

    private IOUtils() {
        // singleton
    }

    public static void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to close resource: ", e);
            }
        }
    }

    /**
     * Delete directory recursively. This method does not follow symlinks.
     * @param path directory to delete
     * @throws IOException if any read error
     */
    public static void removeRecursive(Path path) throws IOException
    {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
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
     * List files in the directory recursively.
     *
     * @param root starting directory     
     * @return recursively traversed list of files with given suffix
     */
    public static List<File> listFilesRec(File root) {
        return listFilesRec(root, null);
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
        File[] files = root.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (suffix != null && !suffix.isEmpty()) {
                    return suffix != null && !suffix.isEmpty() && name.endsWith(suffix);
                } else {
                    return true;
                }
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
            charset = "UTF-16";
            in.reset();
        } else if (br >= 3 && head[0] == (byte) 0xEF && head[1] == (byte) 0xBB
                && head[2] == (byte) 0xBF) {
            // InputStreamReader does not properly discard BOM on UTF8 streams,
            // so don't reset the stream.
            charset = "UTF-8";
        }

        if (charset == null) {
            in.reset();
            charset = defaultCharset;
        }

        return new InputStreamReader(in, charset);
    }
}
