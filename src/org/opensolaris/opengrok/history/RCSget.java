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

import org.apache.commons.jrcs.rcs.*;
import org.apache.commons.jrcs.diff.*;
import java.util.*;
import java.io.*;

/**
 * Virtualise RCS log as an input stream
 */
public class RCSget extends InputStream {
    Archive archive = null;
    Object[] lines;
    int curline, linepos;
    
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
    
        /*
         * Pass null in version to get current revision
         */
    public RCSget(String file)  throws IOException, FileNotFoundException {
        this(file, new FileInputStream(file), null);
    }
    
    public RCSget(String file, String version) throws IOException, FileNotFoundException {
        this(file, new FileInputStream(file), version);
    }
    
    public RCSget(String file, InputStream in, String version) throws IOException, FileNotFoundException{
        //super(in);
        try {
            this.archive = new Archive(file, in);
            if (version != null) {
                this.lines = archive.getRevision(version, false);
            } else {
                this.lines = archive.getRevision(false);
            }
        } catch (ParseException e) {
            throw (new IOException());
        } catch (InvalidFileFormatException e) {
            throw (new IOException("Error: Invalid RCS file format"));
        } catch (PatchFailedException e) {
            throw (new IOException());
        } catch (NodeNotFoundException e) {
            throw (new IOException("Revision " + version + " not found"));
        }
        curline = 0;
        linepos = 0;
    }
    
    public int read(byte[] buffer, int pos, int len) {
        int n = 0;
        if(curline >= lines.length)
            return -1;
        
        while (len > 0 && curline < lines.length) {
            String line = (String) lines[curline];
            if (curline < lines.length - 1) line = line + "\012";
            int linelen = line.length();
            if (( linelen - linepos) <= len) {
                line.getBytes(linepos, linelen, buffer, pos);
                int inc = linelen - linepos;
                curline++;
                pos += inc;
                n += inc;
                len -= inc;
                linepos = 0;
            } else {
                line.getBytes(linepos, linepos + len, buffer, pos);
                pos += len;
                n += len;
                linepos += len;
                len = 0;
            }
        }
        
        return n;
    }
    public int read() throws IOException {
        throw new IOException("use a BufferedInputStream. just read() is not supported!");
    }
}
