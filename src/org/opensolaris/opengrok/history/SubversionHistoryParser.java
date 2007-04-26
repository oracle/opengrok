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
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;

import org.tigris.subversion.javahl.BlameCallback;
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

    /**
     * Parse the history for the specified file.
     *
     * @param file the file to parse history for
     * @param repos ignored
     * @return object representing the file's history
     */
    public History parse(File file, ExternalRepository repos)
        throws IOException, ClientException
    {
        SVNClient client = new SVNClient();

        LogMessage[] messages =
            client.logMessages(file.getPath(), Revision.START, Revision.BASE, false);
        final LinkedHashMap<Long, HistoryEntry> revisions =
            new LinkedHashMap<Long, HistoryEntry>();
        for (LogMessage msg : messages) {
            HistoryEntry entry = new HistoryEntry();
            entry.setRevision(msg.getRevision().toString());
            entry.setDate(msg.getDate());
            entry.setAuthor(msg.getAuthor());
            entry.setMessage(msg.getMessage());
            entry.setActive(true);
            revisions.put(msg.getRevisionNumber(), entry);
        }

        ArrayList<HistoryEntry> entries =
            new ArrayList<HistoryEntry>(revisions.values());
        Collections.reverse(entries);

        History history = new History();
        history.setHistoryEntries(entries);
        return history;
    }

    /**
     * Annotate the specified file.
     *
     * @param file the file to annotate
     * @param revision the revision of the file (<code>null</code>
     * means BASE)
     * @param repository external repository (ignored)
     * @return file annotation
     */
    public Annotation annotate(File file, String revision,
                               ExternalRepository repository)
        throws ClientException
    {
        SVNClient client = new SVNClient();

        final Annotation annotation = new Annotation();
        BlameCallback callback = new BlameCallback() {
                public void singleLine(Date changed, long revision,
                                       String author, String line) {
                    annotation.addLine(Long.toString(revision), author);
                }
            };

        Revision rev = (revision == null) ?
            Revision.BASE : Revision.getInstance(Long.parseLong(revision));
        client.blame(file.getPath(), Revision.START, rev, callback);
        return annotation;
    }

    /**
     * Check whether history should be cached for this parser.
     */
    public boolean isCacheable() {
        return true;
    }

    public boolean supportsAnnotation() {
        return true;
    }
}
