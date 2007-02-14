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

package org.opensolaris.opengrok.history;

import java.util.List;

/**
 * Class representing the history of a file.
 */
public class History {
    /** Entries in the log. */
    private List<HistoryEntry> entries;
    /** List of the last log entry for each line in the file. */
    private List<HistoryEntry> annotation;

    public void setHistoryEntries(List<HistoryEntry> entries) {
        this.entries = entries;
    }

    public List<HistoryEntry> getHistoryEntries() {
        return entries;
    }

    public void setAnnotation(List<HistoryEntry> annotation) {
        this.annotation = annotation;
    }

    public List<HistoryEntry> getAnnotation() {
        return annotation;
    }
}
