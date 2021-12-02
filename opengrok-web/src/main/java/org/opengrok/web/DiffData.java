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
 * Copyright (c) 2009, 2011, Jens Elkner.
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.suigeneris.jrcs.diff.Revision;

/**
 * A simple container to store the data required to generate a view of diffs
 * for a certain versioned file.
 *
 * @author  Jens Elkner
 */
public class DiffData {
    /** the directory which contains the given file wrt. to the source root directory. */
    private final String path;
    /** the HTML escaped filename used. */
    private final String filename;
    /** the genre of the requested diff. */
    AbstractAnalyzer.Genre genre;
    /** the original and new revision container. */
    Revision revision;
    /**
     * the URI encoded parameter values of the request. {@code param[0]}
     * belongs to {@code r1}, {@code param[1]} to {@code r2}.
     */
    String[] param;
    /** the revision names extracted from {@link #param}. */
    String[] rev;
    /** the content of the original and new file line-by-line corresponding with {@link #rev}. */
    String[][] file;
    /** error message to show, if diffs are not available. */
    String errorMsg;
    /** If {@code true} a full diff is desired. */
    boolean full;
    /** How should the data be displayed (request parameter {@code format}. */
    DiffType type;

    public DiffData(String path, String filename) {
        this.path = path;
        this.filename = filename;

        this.rev = new String[2];
        this.file = new String[2][];
        this.param = new String[2];
    }

    public String getPath() {
        return path;
    }

    public String getFilename() {
        return filename;
    }

    public AbstractAnalyzer.Genre getGenre() {
        return genre;
    }

    public Revision getRevision() {
        return revision;
    }

    public String getParam(int index) {
        return param[index];
    }

    public String getRev(int index) {
        return rev[index];
    }

    public String[] getFile(int index) {
        return file[index];
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public boolean isFull() {
        return full;
    }

    public DiffType getType() {
        return type;
    }
}
