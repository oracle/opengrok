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
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.configuration;

import java.io.File;

/**
 * This class maintains a list of directory names, SRC_ROOT
 * relative file paths (like "usr/src/uts"), and glob
 * patterns (like .make.*) which opengrok should ignore.
 *
 * @author Chandan
 */
public final class IgnoredDirs extends Filter {
    private static final long serialVersionUID = -6771850658877396699L;

    private static final String[] defaultPatternsDirs = {
        "Codemgr_wsdata", // Teamware
        "deleted_files",  // Teamware
    };

    public IgnoredDirs() {
        super();
        addDefaultPatterns();
    }

    /**
     * Should the file (that must be a directory) be ignored or not?
     * @return {@code true} if {@code file} has
     * {@link File#isDirectory()} () == true} and should be ignored per
     * filtering
     */
    @Override
    public boolean match(File file) {
        return file.isDirectory() && super.match(file);
    }

    private void addDefaultPatterns() {
        for (String s : defaultPatternsDirs) {
            add(s);
        }
    }
}
