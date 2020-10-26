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
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.List;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.opengrok.indexer.configuration.IgnoredNames;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import static org.junit.Assert.assertNotNull;

/**
 * Various filesystem utilities used by the different test setups.
 *
 * @author Trond Norbye
 */
public class FileUtilities {

    public static void extractArchive(File sourceBundle, File root) throws IOException {
        try (ZipFile zipfile = new ZipFile(sourceBundle)) {
            Enumeration<ZipArchiveEntry> e = zipfile.getEntries();

            while (e.hasMoreElements()) {
                ZipArchiveEntry ze = e.nextElement();
                File file = new File(root, ze.getName());
                if (ze.isUnixSymlink()) {
                    File target = new File(file.getParent(), zipfile.getUnixSymlink(ze));
                    /*
                     * A weirdness is that an object may already have been
                     * exploded before the symlink entry is reached in the
                     * ZipFile. So unlink any existing entry to avoid an
                     * exception on creating the symlink.
                     */
                    if (file.isDirectory()) {
                        removeDirs(file);
                    } else if (file.exists()) {
                        file.delete();
                    }
                    Files.createSymbolicLink(file.toPath(), target.toPath());
                } else if (ze.isDirectory()) {
                    file.mkdirs();
                } else {
                    try (InputStream in = zipfile.getInputStream(ze);
                         OutputStream out = new FileOutputStream(file)) {
                        if (in == null) {
                            throw new IOException("Cannot get InputStream for " + ze);
                        }
                        copyFile(in, out);
                    }
                }
            }
        }
    }

    public static boolean removeDirs(File root) {
        for (File f : root.listFiles()) {
            if (f.isDirectory()) {
                removeDirs(f);
            } else {
                f.delete();
            }
        }
        return root.delete();
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] array = new byte[8192];
        int nr;

        while ((nr = in.read(array)) > 0) {
            out.write(array, 0, nr);
        }
        out.flush();
    }

    public static void getAllFiles(File root, List<File> files, boolean directories) {
        assertNotNull(files);
        if (directories) {
            files.add(root);
        }

        IgnoredNames ignore = RuntimeEnvironment.getInstance().getIgnoredNames();

        for (File f : root.listFiles()) {
            if (!ignore.ignore(f)) {
                if (f.isDirectory()) {
                    getAllFiles(f, files, directories);
                } else {
                    files.add(f);
                }
            }
        }
    }

    private FileUtilities() {
    }

    /**
     * Determine if given program is present in one of the directories
     * in PATH environment variable.
     *
     * @param  progName name of the program
     * @return absolute path to the program or null
     */
    public static File findProgInPath(String progName) {
        String systemPath = System.getenv("PATH");
        if (systemPath == null) {
             return null;
        }

        String[] pathDirs = systemPath.split(File.pathSeparator);
        File absoluteFile = null;

        for (String dir : pathDirs) {
            File file = new File(dir, progName);
            if (file.isFile() && file.canExecute()) {
                absoluteFile = file;
                break;
            }
        }
        return absoluteFile;
    }
}
