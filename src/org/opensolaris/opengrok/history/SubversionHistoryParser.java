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
 * ident	"%Z%%M% %I%     %E% SMI"
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.tigris.subversion.javahl.ClientException;
import org.tigris.subversion.javahl.SVNClient;
import org.tigris.subversion.javahl.Revision;
import org.tigris.subversion.javahl.LogMessage;

// This is a rewrite of the class that was previously called
// SubversionHistoryReader

/**
 * Read out version history for a given file.
 *
 * @author Trond Norbye
 */
public class SubversionHistoryParser implements HistoryParser {

    public List<HistoryEntry> parse(File file, ExternalRepository repos)
        throws IOException, ClientException
    {
        SVNClient client = new SVNClient();
        LogMessage[] messages =
            client.logMessages(file.getPath(), Revision.START, Revision.HEAD);
        List<HistoryEntry> history = new ArrayList<HistoryEntry>();
        for (LogMessage msg : messages) {
            HistoryEntry entry = new HistoryEntry();
            entry.setRevision(msg.getRevision().toString());
            entry.setDate(msg.getDate());
            entry.setAuthor(msg.getAuthor());
            entry.setMessage(msg.getMessage());
            entry.setActive(true);
            history.add(entry);
        }
        return history;
    }

    public boolean isCacheable() {
        return true;
    }
}
