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

/**
 * Access to a Bazaar repository.
 * 
 */
public class BazaarRepository extends ExternalRepository {
    private String command;
    
    /**
     * Creates a new instance of BazaarRepository
     */
    public BazaarRepository() { }
    
    /**
     * Creates a new instance of BazaarRepository
     * @param directory The directory containing the .bzr-subdirectory
     */
    public BazaarRepository(String directory) {
        setDirectoryName(new File(directory).getAbsolutePath());
        command = System.getProperty("org.opensolaris.opengrok.history.Bazaar", "/Users/trond/bin/bzr");
    }
    
    /**
     * Set the name of the Bazaar command to use
     * @param command the name of the command (bzr)
     */
    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * Get the name of the Bazaar command that should be used
     * @return the name of the bzr command in use
     */
    public String getCommand() {
        return command;
    }
        
    Process getHistoryLogProcess(File file) throws IOException {
        String abs = file.getAbsolutePath();
        String filename = "";
        String directoryName = getDirectoryName();
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        }
        
        String argv[];
        if (file.isDirectory()) {
            argv = new String[] { command, "log", "-v", filename };
        } else {
            argv = new String[] { command, "log", filename };
        }

        File directory = new File(getDirectoryName());
        return Runtime.getRuntime().exec(argv, null, directory);        
    }    
    
    public InputStream getHistoryGet(String parent, String basename, String rev) {
        InputStream ret = null;

        String directoryName = getDirectoryName();
        File directory = new File(directoryName);

        String filename =  (new File(parent, basename)).getAbsolutePath().substring(directoryName.length() + 1);
        Process process = null;
        try {
            String argv[] = { command, "cat", "-r", rev, filename };
            process = Runtime.getRuntime().exec(argv, null, directory);

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
        } catch (Exception exp) {
            System.err.print("Failed to get history: " + exp.getClass().toString());
            exp.printStackTrace();
        } finally {
            // Clean up zombie-processes...
            if (process != null) {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException exp) {
                    // the process is still running??? just kill it..
                    process.destroy();
                }
            }
        }
        
        return ret;
    }
    
    public Class<? extends HistoryParser> getHistoryParser() {
        return BazaarHistoryParser.class;
    }

    public Class<? extends HistoryParser> getDirectoryHistoryParser() {
        return BazaarHistoryParser.class;
    }

    public Annotation annotate(File file, String revision) throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean supportsAnnotation() {
        return false;
    }

    public boolean isCacheable() {
        return true;
    }
    
    public void update() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean fileHasHistory(File file) {
        // Todo: is there a cheap test for whether Bazaar has history
        // available for a file?
        // Otherwise, this is harmless, since Bazaar's commands will just
        // print nothing if there is no history.
        return true;
    }
}
