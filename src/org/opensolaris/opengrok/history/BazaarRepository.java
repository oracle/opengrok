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
import java.util.List;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.util.Executor;

/**
 * Access to a Bazaar repository.
 * 
 */
public class BazaarRepository extends Repository {
    private static ScmChecker bzrBinary = new ScmChecker(new String[] {
        System.getProperty("org.opensolaris.opengrok.history.Bazaar", "bzr"),
        "--help" });
    
   /**
     * Get the name of the Bazaar command that should be used.
     * 
     * @return the name of the bzr command in use
     */
    private String getCommand() {
        return System.getProperty("org.opensolaris.opengrok.history.Bazaar", "bzr");
    }
    
    Process getHistoryLogProcess(File file) throws IOException {
        String abs = file.getAbsolutePath();
        String filename = "";
        String command = getCommand();
        String directoryName = getDirectoryName();
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        }

        String argv[];
        if (file.isDirectory()) {
            argv = new String[] {command, "log", "-v", filename};
        } else {
            argv = new String[] {command, "log", filename};
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
            String argv[] = {getCommand(), "cat", "-r", rev, filename};
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
            OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to get history: " + exp.getClass().toString(), exp);
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

    public Annotation annotate(File file, String revision) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean fileHasAnnotation(File file) {
        return false;
    }

    public boolean isCacheable() {
        return true;
    }
    
    public void update() throws IOException {
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<String>();
        cmd.add(getCommand());
        cmd.add("info");
        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }

        if (executor.getOutputString().indexOf("parent branch:") != -1) {
            cmd.clear();
            cmd.add(getCommand());
            cmd.add("up");
            executor = new Executor(cmd, directory);
            if (executor.exec() != 0) {
                throw new IOException(executor.getErrorString());
            }
        }
    }

    public boolean fileHasHistory(File file) {
        // Todo: is there a cheap test for whether Bazaar has history
        // available for a file?
        // Otherwise, this is harmless, since Bazaar's commands will just
        // print nothing if there is no history.
        return true;
    }

    @Override
    boolean isRepositoryFor(File file) {
        File f = new File(file, ".bzr");
        return f.exists() && f.isDirectory();
    }
    
    @Override
    protected boolean isWorking() {
        return bzrBinary.available;
    }
}
