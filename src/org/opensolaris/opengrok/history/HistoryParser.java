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
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

/**
 * Interface for parsers which read a history log and return a list of history
 * entries.
 */
interface HistoryParser {
    /**
     * Parse the history log for the given file.
     *
     * @param file the file
     * @param repository the external repository to fetch the history from
     * (could be null if no external repository is used)
     * @return list of <code>HistoryEntry</code> objects
     */
    List<HistoryEntry> parse(File file, ExternalRepository repository)
        throws Exception;
    /**
     * Check whether the parsed history should be cached.
     *
     * @return <code>true</code> if the history should be cached
     */
    boolean isCacheable();
}
