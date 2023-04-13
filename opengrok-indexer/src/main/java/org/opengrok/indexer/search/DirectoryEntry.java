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
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search;

import org.opengrok.indexer.analysis.NullableNumLinesLOC;

import java.io.File;
import java.util.Date;

/**
 * Represents a pairing of {@link File} along with supplemental
 * {@link NullableNumLinesLOC}.
 */
public class DirectoryEntry {

    private final File file;
    private final NullableNumLinesLOC extra;

    private String description;

    private Date date;

    /**
     * Initializes an instance with a specified, required {@link File}.
     * @param file a defined instance
     */
    public DirectoryEntry(File file) {
        this(file, null);
    }

    /**
     * Initializes an instance with a specified, required {@link File} and
     * a possible {@link NullableNumLinesLOC}.
     * @param file a defined instance
     * @param extra an optional instance
     */
    public DirectoryEntry(File file, NullableNumLinesLOC extra) {
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
    public NullableNumLinesLOC getExtra() {
        return extra;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }
}
