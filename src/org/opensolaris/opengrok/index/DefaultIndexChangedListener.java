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
package org.opensolaris.opengrok.index;

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

    DefaultIndexChangedListener() {
        verbose = RuntimeEnvironment.getInstance().isVerbose();
    }
    
    public void fileAdded(String path, String analyzer) {
        if (verbose) {
            synchronized (this) {
                System.out.println("Added: " + path + " (" + analyzer + ")");
            }
        }
    }

    public void fileRemoved(String path) {
        if (verbose) {
            synchronized (this) {
                System.out.println("Remove stale file: " + path);
            }
        }
    }
}
