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
package org.opensolaris.opengrok.history;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;


public class SCCSget {
    public static InputStream getRevision(File file, String revision) throws IOException {
        InputStream ret = null;
        String command = System.getProperty("org.opensolaris.opengrok.history.Teamware", "sccs");

        ArrayList<String> argv = new ArrayList<String>();
        argv.add(command);
        argv.add("get");
        argv.add("-p");
        if (revision != null) {
            argv.add("-r");
            argv.add(revision);
        }
        argv.add(file.getCanonicalPath());
        ProcessBuilder pb = new ProcessBuilder(argv);
        Process process = null;
        try {
            process = pb.start();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[32 * 1024];
            InputStream in = process.getInputStream();
            int len;

            while ((len = in.read(buffer)) != -1) {
                if (len > 0) {
                    out.write(buffer, 0, len);
                }
            }

            ret = new BufferedInputStream(new ByteArrayInputStream(out.toByteArray()));
        } finally {
            // is this really the way to do it? seems a bit brutal...
            try {
                if (process != null) {
                    process.exitValue();
                }
            } catch (IllegalThreadStateException e) {
                process.destroy();
            }
        }

        return ret;
    }

    private SCCSget() {
    }
}
