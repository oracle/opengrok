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

import java.io.IOException;

/**
 *
 * @author Trond Norbye
 */
class ScmChecker {

    boolean available;

    ScmChecker(final String[] argv) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(argv);
            boolean done = false;
            do {
                try {
                    process.waitFor();
                    done = true;
                } catch (InterruptedException exp) {
                    // Ignore
                    }
            } while (!done);
            if (process.exitValue() == 0) {
                available = true;
            }
        } catch (IOException exp) {
            // Ignore the exception.. most likely the process doesn't exists
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
    }
}
