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

import java.io.File;
import java.util.ArrayList;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 *
 * @author Trond Norbye
 */
public class DirectoryHistoryParser implements HistoryParser {
    
    /** Creates a new instance of DirectoryHistoryParser */
    public DirectoryHistoryParser() {
    }
    
    public History parse(File file, Repository repository)
            throws Exception {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String filename = file.getCanonicalPath().substring(env.getSourceRootPath().length());
        ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        HistoryReader hr = new DirectoryHistoryReader(filename);
        try {
            while (hr.next()) {
                HistoryEntry ent = new HistoryEntry(
                        hr.getRevision(), hr.getDate(), hr.getAuthor(),
                        hr.getComment(), hr.isActive());
                ent.setFiles(hr.getFiles());
                entries.add(ent);
            }
        } finally {
            hr.close();
        }
        History history = new History();
        history.setHistoryEntries(entries);
        return history;
    }

    public Annotation annotate(File file, String revision,
                               Repository repository) {
        return null;
    }

    public boolean isCacheable() {
        return false;
    }
}
