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
package org.opensolaris.opengrok.history;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.jrcs.diff.PatchFailedException;
import org.apache.commons.jrcs.rcs.Archive;
import org.apache.commons.jrcs.rcs.InvalidFileFormatException;
import org.apache.commons.jrcs.rcs.NodeNotFoundException;
import org.apache.commons.jrcs.rcs.ParseException;

/**
 * Virtualise RCS log as an input stream
 */
public class RCSget extends InputStream {
    private BufferedInputStream stream;
        
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
            
            if (version == null) {
                lines = archive.getRevision(false);
            } else {
                lines = archive.getRevision(version, false);
            }
            
            StringBuilder sb = new StringBuilder();
            for (int ii = 0; ii < lines.length; ++ii) {
                sb.append((String)lines[ii]);
                sb.append("\n");
            }
            stream = new BufferedInputStream(new ByteArrayInputStream(sb.toString().getBytes()));
        } catch (ParseException e) {
            throw (new IOException(e.getMessage()));
        } catch (InvalidFileFormatException e) {
            throw (new IOException("Error: Invalid RCS file format " + e.getMessage()));
        } catch (PatchFailedException e) {
            throw (new IOException(e.getMessage()));
        } catch (NodeNotFoundException e) {
            throw (new IOException("Revision " + version + " not found" + e.getMessage()));
        }
    }
    
    @Override
    public void reset() throws IOException {
        stream.reset();
    }
    
    @Override
    public void close() throws IOException {
        if (stream != null) {
            stream.close();
        }
    }
    
    @Override
    public void mark(int readlimit) {
        stream.mark(readlimit);
    }
    
    @Override
    public int read(byte[] buffer, int pos, int len) throws IOException {
        return stream.read(buffer, pos, len);
    }
    
    public int read() throws IOException {
        throw new IOException("use a BufferedInputStream. just read() is not supported!");
    }
}
