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
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search;

import java.io.File;

/**
 * Represents a pairing of {@link File} along with supplemental
 * {@link FileExtra}.
 */
public class DirectoryEntry {

    private final File file;
    private final FileExtra extra;

    /**
     * Initializes an instance with a specified, required {@link File}.
     * @param file a defined instance
     */
    public DirectoryEntry(File file) {
        this(file, null);
    }

    /**
     * Initializes an instance with a specified, required {@link File} and
     * a possible {@link FileExtra}.
     * @param file a defined instance
     * @param extra an optional instance
     */
    public DirectoryEntry(File file, FileExtra extra) {
        if (file == null) {
            throw new IllegalArgumentException("`file' is null");
        }
        this.file = file;
        this.extra = extra;
    }

    /**
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * @return the (optional) extra file data
     */
    public FileExtra getExtra() {
        return extra;
    }
}
