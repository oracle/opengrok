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

import java.io.File;
import java.io.InputStream;
import java.net.Socket;

/**
 *
 * @author Trond Norbye
 */
public class MercurialRepository implements ExternalRepository {
    private File directory;
    private String command;
    private boolean verbose;
    /**
     * Creates a new instance of MercurialRepository
     */
    public MercurialRepository(String directory) {
        this.directory = new File(directory);
        command = System.getProperty("org.opensolaris.opengrok.history.Mercurial", "hg");
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
    
    public File getDirectory() {
        return directory;
    }
     
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
    
    public Socket getDaemonSocket() {
        return null;
    }
    
    public HistoryReader getHistoryReader(String parent, String basename) {
        MercurialHistoryReader ret = null;
        try {
            ret = new MercurialHistoryReader(new File(parent, basename));
            ret.initialize(this);
        } catch (Exception ex) {
            ex.printStackTrace();
            ret = null;
        }
        return ret;
    }

    public InputStream getHistoryGet(String parent, String basename, String rev) {
        MercurialGet ret = null;

        try {
            ret = new MercurialGet(command, directory, new File(parent, basename), rev);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return ret;
    }
}
