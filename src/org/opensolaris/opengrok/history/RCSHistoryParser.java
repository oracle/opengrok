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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)RCSHistoryReader.java 1.2     05/12/01 SMI"
 */

package org.opensolaris.opengrok.history;

import org.apache.commons.jrcs.rcs.*;
import java.util.*;
import java.io.*;
import org.opensolaris.opengrok.web.Util;

// This is a rewrite of the class that was previously called
// RCSHistoryReader

/**
 * Virtualise RCS file as a reader, getting a specified version
 */
public class RCSHistoryParser implements HistoryParser {

    public History parse(File file, ExternalRepository repos)
        throws IOException, ParseException
    {
        Archive archive = new Archive(Util.getRCSFile(file).getPath());
        Version ver = archive.getRevisionVersion();
        Node n = archive.findNode(ver);
        n = n.root();

        ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        traverse(n, entries);

        History history = new History();
        history.setHistoryEntries(entries);
        return history;
    }

    private void traverse(Node n, List<HistoryEntry> history) {
        if (n== null)
            return;
        traverse(n.getChild(), history);
        TreeMap brt = n.getBranches();
        if (brt != null) {
            for (Iterator i = brt.values().iterator(); i.hasNext();) {
                Node b = (Node) i.next();
                traverse(b, history);
            }
        }
        if(!n.isGhost()) {
            HistoryEntry entry = new HistoryEntry();
            entry.setRevision(n.getVersion().toString());
            entry.setDate(n.getDate());
            entry.setAuthor(n.getAuthor());
            entry.setMessage(n.getLog());
            entry.setActive(true);
            history.add(entry);
        }
    }

    public boolean isCacheable() {
        // repository is stored locally, no need to cache
        return false;
    }
}
