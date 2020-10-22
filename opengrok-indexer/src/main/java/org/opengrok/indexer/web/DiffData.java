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
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.web;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.suigeneris.jrcs.diff.Revision;

/**
 * A simple container to store the data required to generated a view of diffs
 * for a certain versioned file.
 *
 * @author  Jens Elkner
 * @version $Revision$
 */
public class DiffData {

    /** the directory which contains the given file wrt. to the source root directory. */
    public String path;
    /** the HTML escaped filename used. */
    public String filename;
    /** the genre of the requested diff. */
    public AbstractAnalyzer.Genre genre;
    /** the original and new revision container. */
    public Revision revision;
    /** the URI encoded parameter values of the request. {@code param[0]}
     * belongs to {@code r1}, {@code param[1]} to {@code r2}. */
    public String[] param;
    /** the revision names extracted from {@link #param}. */
    public String[] rev;
    /** the content of the original and new file line-by-line corresponding
     * with {@link #rev}. */
    public String[][] file;
    /** error message to show, if diffs are not available. */
    public String errorMsg;
    /** If {@code true} a full diff is desired. */
    public boolean full;
    /** How should the data be displayed (request parameter {@code format}. */
    public DiffType type;
}
