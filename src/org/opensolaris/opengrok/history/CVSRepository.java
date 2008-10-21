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
import java.util.ArrayList;
import java.util.List;
import org.opensolaris.opengrok.util.Executor;

/**
 * Access to a local CVS repository.
 */
public class CVSRepository extends RCSRepository {
    
   /**
     * Get the name of the Cvs command that should be used
     * 
     * @return the name of the cvs command in use
     */
    private static String getCommand() {
        return System.getProperty("org.opensolaris.opengrok.history.cvs", "cvs");
    }
    
    @Override
    File getRCSFile(File file) {
        File cvsFile =
                RCSHistoryParser.getCVSFile(file.getParent(), file.getName());
        if (cvsFile != null && cvsFile.exists()) {
            return cvsFile;
        } else {
            return null;
        }
    }

    @Override
    public boolean isRepositoryFor(File file) {
        File cvsDir = new File(file, "CVS");
        return cvsDir.isDirectory();
    }
    
    @Override
    public void update() throws IOException {
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<String>();
        cmd.add(getCommand());
        cmd.add("update");
        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }
    }
}
