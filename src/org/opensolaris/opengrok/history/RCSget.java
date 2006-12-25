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
 * ident	"@(#)RCSget.java 1.3     05/12/02 SMI"
 */

package org.opensolaris.opengrok.history;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Date;
import org.apache.commons.jrcs.diff.PatchFailedException;
import java.io.IOException;
import org.apache.commons.jrcs.rcs.Archive;
import org.apache.commons.jrcs.rcs.InvalidFileFormatException;
import org.apache.commons.jrcs.rcs.NodeNotFoundException;
import org.apache.commons.jrcs.rcs.ParseException;

/**
 * Virtualise RCS log as an input stream
 */
public class RCSget extends InputStream {
    private BufferedInputStream stream;
    
    public static void main(String[] args) {
        try{
            long t1 = (new Date()).getTime();
            RCSget rr = new RCSget(args[0], args[1]);
            byte[] text = new byte[1024];
            int l;
            while ((l = rr.read(text)) > 0) {
                System.out.write(text, 0 , l);
            }
            System.err.println("Took " + ((new Date()).getTime() - t1));
        } catch (Exception e) {
            System.err.println("Usage RCSget file revision " + e);
            e.printStackTrace();
        }
    }
    
    /**
     * Pass null in version to get current revision
     */
    public RCSget(String file)  throws IOException, FileNotFoundException {
        this(file, new FileInputStream(file), null);
    }
    
    public RCSget(String file, String version) throws IOException, FileNotFoundException {
        this(file, new FileInputStream(file), version);
    }
    
    public RCSget(String file, InputStream in, String version) throws IOException, FileNotFoundException{
        try {
            Archive archive = new Archive(file, in);
            Object[] lines;
            
            if (version != null) {
                lines = archive.getRevision(version, false);
            } else {
                lines = archive.getRevision(false);
            }
            
            StringBuilder sb = new StringBuilder();
            for (int ii = 0; ii < lines.length; ++ii) {
                sb.append((String)lines[ii]);
                sb.append("\n");
            }
            stream = new BufferedInputStream(new ByteArrayInputStream(sb.toString().getBytes()));
        } catch (ParseException e) {
            throw (new IOException());
        } catch (InvalidFileFormatException e) {
            throw (new IOException("Error: Invalid RCS file format"));
        } catch (PatchFailedException e) {
            throw (new IOException());
        } catch (NodeNotFoundException e) {
            throw (new IOException("Revision " + version + " not found"));
        }
    }
    
    public void reset() throws IOException {
        try {
            stream.reset();
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }
    
    public void close() throws IOException {
        if (stream != null) {
            stream.close();
        }
    }
    
    public void mark(int readlimit) {
        stream.mark(readlimit);
    }
    
    public int read(byte[] buffer, int pos, int len) throws IOException {
        return stream.read(buffer, pos, len);
    }
    
    public int read() throws IOException {
        throw new IOException("use a BufferedInputStream. just read() is not supported!");
    }
}
