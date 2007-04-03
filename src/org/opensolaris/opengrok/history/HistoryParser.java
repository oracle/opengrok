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
 * Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.history;

import java.io.File;
import java.util.List;

/**
 * Interface for parsers which read a history log and return an object
 * representing the history of the file.
 */
interface HistoryParser {
    /**
     * Parse the history log for the given file.
     *
     * @param file the file
     * @param repository the external repository to fetch the history from
     * (could be null if no external repository is used)
     * @return the history of the file
     */
    History parse(File file, ExternalRepository repository)
        throws Exception;

    /**
     * Annotate (aka praise/blame) the specified file.
     *
     * @param file the file
     * @param revision which revision of the file to annotate, or
     * <code>null</code> to annotate the checked out revision
     * @param repository the external repository (or <code>null</code>
     * if no external repository is used)
     * @return a list of <code>LineInfo</code> objects which describe
     * each line of the file, or <code>null</code> if annotation is
     * not supported for this <code>HistoryParser</code>
     */
    List<LineInfo> annotate(File file, String revision,
                            ExternalRepository repository)
        throws Exception;

    /**
     * Check whether the parsed history should be cached.
     *
     * @return <code>true</code> if the history should be cached
     */
    boolean isCacheable();
}
