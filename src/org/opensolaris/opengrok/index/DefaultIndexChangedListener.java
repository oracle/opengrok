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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.index;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 * Print the index modifications to the standard output stream when running
 * in verbose mode. In the quiet mode all events are just silently ignored.
 * 
 * @author Trond Norbye
 */
@SuppressWarnings("PMD.SystemPrintln")
class DefaultIndexChangedListener implements IndexChangedListener {

    private final boolean verbose;
    private static final Logger log = Logger.getLogger(DefaultIndexChangedListener.class.getName());

    DefaultIndexChangedListener() {
        verbose = RuntimeEnvironment.getInstance().isVerbose();
    }
    
    @Override
    public void fileAdd(String path, String analyzer) {        
        if (verbose) {
            synchronized (this) {                
                System.out.println("Add: " + path + " (" + analyzer + ")");
            }
        }
        if (log.isLoggable(Level.INFO)) {
            log.log(Level.INFO, "Add: {0} ({1})", new Object[]{path, analyzer});}
    }

    @Override
    public void fileRemove(String path) {
        if (verbose) {
            synchronized (this) {
                System.out.println("Remove stale file: " + path);
            }
        }
        log.log(Level.INFO, "Remove file:{0}", path);
    }
    @Override
    public void fileUpdate(String path) {
        if (verbose) {
            synchronized (this) {
                System.out.println("Update: " + path);
            }
        }
        log.log(Level.INFO, "Update: {0}", path);
    }

    @Override
    public void fileAdded(String path, String analyzer) {
        if (log.isLoggable(Level.FINER)) {
            log.log(Level.FINER, "Added: {0} ({1})", new Object[]{path, analyzer});}
    }

    @Override
    public void fileRemoved(String path) {
        log.log(Level.FINER, "Removed file:{0}", path);
    }
}
