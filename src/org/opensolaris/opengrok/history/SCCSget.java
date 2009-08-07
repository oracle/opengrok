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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import org.opensolaris.opengrok.util.Executor;


public final class SCCSget {
           
    public static InputStream getRevision(String command, File file, String revision) throws IOException {
        InputStream ret = null;
        ArrayList<String> argv = new ArrayList<String>();
        argv.add(command);
        argv.add("get");
        argv.add("-p");
        if (revision != null) {
            argv.add("-r"+revision);
        }
        argv.add(file.getCanonicalPath());

        Executor executor = new Executor(argv);
        if (executor.exec() == 0) {
            ret = executor.getOutputStream();
        }

        return ret;
    }

    private SCCSget() {
    }
}
