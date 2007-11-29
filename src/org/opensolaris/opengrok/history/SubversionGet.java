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
 * ident	"%Z%%M% %I%     %E% SMI"
 */
package org.opensolaris.opengrok.history;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

import org.tigris.subversion.javahl.ClientException;
import org.tigris.subversion.javahl.Info;
import org.tigris.subversion.javahl.Revision;
import org.tigris.subversion.javahl.SVNClient;

/**
 * A simple implementation to get a named revision of a file
 * @author Trond Norbye
 */
public class SubversionGet extends InputStream {
    private ByteArrayInputStream input;
    private String parent;
    private String name;
    private String rev;
    private File dst;
    
    /** Creates a new instance of SubversionGet */
    public SubversionGet(String parent, String name, String rev) {
        this.parent = parent;
        this.name = name;
        this.rev = rev;
        input = null;
    }
    
    public int read(byte[] b, int off, int len) throws IOException {
        if (input == null) {
            long revision;
            try {
                revision = Long.parseLong(rev);
            } catch (NumberFormatException exp) {
                throw new IOException("Failed to retrieve rev (" + rev + "): Not a valid Subversion revision format");
            }
            
            
	    RuntimeEnvironment env = RuntimeEnvironment.getInstance();
	    String workingCopy = RuntimeEnvironment.getInstance().getSourceRootFile().getAbsolutePath();

            SVNClient client = new SVNClient();

            byte data[];
            try {
                Info info = client.info(workingCopy);
		String wcUrl = info.getUrl();

		String svnPath = parent + "/" + name;

		// erase the working copy from the path to get the fragment
		svnPath = svnPath.substring(workingCopy.length());

                data = client.fileContent(wcUrl + svnPath, Revision.getInstance(revision));
            } catch (ClientException ex) {
                ex.printStackTrace();
                throw new IOException("Failed to retrieve rev (" + rev + "): " + ex.toString());
            }
            input = new ByteArrayInputStream(data);
        }
        
        return input.read(b, off, len);
    }
    
    public void reset() throws IOException {
        input.reset();
    }
    
    public void mark(int readlimit) {
        input.mark(readlimit);
    }
    
    public int read() throws java.io.IOException {
        throw new IOException("use a BufferedInputStream. just read() is not supported!");
    }
    
    public void close() throws IOException {
    }
}
