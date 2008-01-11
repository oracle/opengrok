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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)SCCSget.java 1.1     05/11/11 SMI"
 */

package org.opensolaris.opengrok.history;

import java.io.*;
import java.util.*;


public class SCCSget extends InputStream {
    private BufferedInputStream stream;
    
    public SCCSget(String file) throws IOException, FileNotFoundException {
        this(file, null);
    }
    
    /**
     * Pass null in version to get current revision
     */
    public SCCSget(String file, String revision) throws IOException, FileNotFoundException {
        String command = System.getProperty("org.opensolaris.opengrok.history.Teamware", "sccs");
        
        ArrayList<String> argv = new ArrayList<String>();
        argv.add(command);
        argv.add("get");
        argv.add("-p");
        if (revision != null) {
            argv.add("-r" + revision);
        }        
        argv.add(file);
        ProcessBuilder pb = new ProcessBuilder(argv);
        Process process = pb.start();
        StringBuilder strbuf = new StringBuilder();
        String line;
        
        try {
            BufferedReader in =
                new BufferedReader(new InputStreamReader
                                     (process.getInputStream()));
            while ((line = in.readLine()) != null) {
                strbuf.append(line);
                strbuf.append("\n");
            }
        } finally {
            // is this really the way to do it? seems a bit brutal...
            try {
                process.exitValue();
            } catch (IllegalThreadStateException e) {
                process.destroy();
            }
        }
        stream = new BufferedInputStream(new ByteArrayInputStream(strbuf.toString().getBytes()));
    }

    @Override public void reset() throws IOException {
        try {
            stream.reset();
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }
    
    @Override public void close() throws IOException {
        if (stream != null) {
            stream.close();
        }
    }
    
    @Override public void mark(int readlimit) {
        stream.mark(readlimit);
    }
    
    @Override public int read(byte[] buffer, int pos, int len) throws IOException {
        return stream.read(buffer, pos, len);
    }
    
    public int read() throws IOException {
        throw new IOException("use a BufferedInputStream. just read() is not supported!");
    }
}
