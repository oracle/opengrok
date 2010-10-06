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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.index;

import java.io.File;

/**
 * This class maintains a list of file names (like "cscope.out"), SRC_ROOT relative
 * file paths (like "usr/src/uts" or "usr/src/Makefile"), and glob patterns
 * (like .make.*) which opengrok should ignore.
 *
 * @author Chandan
 */
public final class IgnoredNames extends Filter {
    private static final String[] defaultPatterns = {
        "SCCS",
        "CVS",
        "RCS",
        "cscope.in.out",
        "cscope.out.po",
        "cscope.out.in",
        "cscope.po.out",
        "cscope.po.in",
        "cscope.files",
        "cscope.out",
        "Codemgr_wsdata",
        ".cvsignore",
        "CVSROOT",
        "TAGS",
        "tags",
        ".svn",
        ".git",
        ".repo",
        ".hg",
        ".hgtags",
        ".bzr",
        ".p4config",
        ".razor",
        "*~",
        "deleted_files",
        ".make.*",
        ".del-*",
        "_MTN"
    };
    
    public IgnoredNames() {
        super();
        addDefaultPatterns();
    }
        
    /**
     * Should the file be ignored or not?
     * @param file the file to check
     * @return true if this file should be ignored, false otherwise
     */
    public boolean ignore(File file) {
        return match(file);
    }
    
    /**
     * Should the file be ignored or not?
     * @param name the name of the file to check
     * @return true if this pathname should be ignored, false otherwise
     */
    public boolean ignore(String name) {
        return match(name);
    }

    private void addDefaultPatterns() {
        for (String s : defaultPatterns) {
            add(s);
        }
    }    
}
