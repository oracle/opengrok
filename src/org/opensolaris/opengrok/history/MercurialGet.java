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
 * Copyright 2006 Trond Norbye.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * A simple implementation to get a named revision of a file
 * @author Trond Norbye
 */
public class MercurialGet extends InputStream {
    private BufferedInputStream stream;
    
    /** Creates a new instance of MercurialGet */
    public MercurialGet(String command, File directory, File file, String rev) throws Exception {
        try {
            String argv[] = { command, "cat", "-r", rev, file.getAbsolutePath() };
            Process process = Runtime.getRuntime().exec(argv, null, directory);
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuffer sb = new StringBuffer();
            String s;
            while ((s = in.readLine()) != null) {
                sb.append(s);
                sb.append("\n");
            }
            
            stream = new BufferedInputStream(new ByteArrayInputStream(sb.toString().getBytes()));
        } catch (Exception exp) {
            System.err.print("Failed to get history: " + exp.getClass().toString());
            System.err.println(exp.getMessage());
            throw exp;
        }
    }
    
    public void reset() throws IOException {
        try {
            stream.reset();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            e.printStackTrace(System.err);
            throw e;
        }
    }
    
    public void mark(int readlimit) {
        stream.mark(readlimit);
    }
    
    
    
    public int read(byte[] b, int off, int len) throws IOException {
        int ret;
        try {
            ret = stream.read(b, off, len);
        }catch (Exception e) {
            System.err.println("Mercurial Get (read): " + e.getMessage());
            e.printStackTrace(System.err);
            ret = -1;
        }
        
        return ret;
    }
    
    public int read() throws java.io.IOException {
        int ret;
        try {
            ret = stream.read();
        }catch (Exception e) {
            System.err.println("Mercurial Get (read()): " + e.getMessage());
            e.printStackTrace(System.err);
            ret = -1;
        }
        
        return ret;
    }
    
    public void close() throws IOException {
        try {
            stream.close();
        } catch (IOException e) {
            System.err.println("Mercurial Get (close): " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
