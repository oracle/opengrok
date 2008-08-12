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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.index.IgnoredNames;
import static org.junit.Assert.*;

/**
 * Various filesystem utilities used by the different test setups
 *
 * @author Trond Norbye
 */
public class FileUtilities {

    public static void extractArchive(File sourceBundle, File root) throws IOException {
        ZipFile zipfile = new ZipFile(sourceBundle);

        Enumeration<? extends ZipEntry> e = zipfile.entries();

        while (e.hasMoreElements()) {
            ZipEntry ze = e.nextElement();
            File file = new File(root, ze.getName());
            if (ze.isDirectory()) {
                file.mkdirs();
            } else {
                InputStream in = zipfile.getInputStream(ze);
                assertNotNull(in);
                FileOutputStream out = new FileOutputStream(file);
                assertNotNull(out);
                copyFile(in, out);
            }
        }
    }

    public static void removeDirs(File root) {
        for (File f : root.listFiles()) {
            if (f.isDirectory()) {
                removeDirs(f);
            } else {
                f.delete();
            }
        }
        root.delete();
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

}
