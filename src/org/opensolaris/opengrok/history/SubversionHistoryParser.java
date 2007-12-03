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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;

import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

import org.tigris.subversion.javahl.BlameCallback;
import org.tigris.subversion.javahl.ChangePath;
import org.tigris.subversion.javahl.ClientException;
import org.tigris.subversion.javahl.Info;
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
        throws IOException, ClientException {
        SVNClient client = new SVNClient();
        History history = new History();

        // Get the working copy's view of the file.
        // This will reconcile the repository's view of the file
        // and OpenGrok's view of the file.
        String workingCopy = null;

        if (repos instanceof SubversionRepository) {
            workingCopy = ((SubversionRepository)repos).getDirectoryName();
        } else {
            workingCopy = RuntimeEnvironment.getInstance().getSourceRootFile().getAbsolutePath();
        }
        Info info = client.info(workingCopy);

        String wcUrl = info.getUrl();
        String wcRepoUrl = info.getRepository();

        // Reconcile the path. If the source root is an arbitrary directory in the repository,
        // (e.g. /trunk), and not the whole repository, this path will need to be removed from
        // the front of each file, if possible.
        final String leadingPathFragment = wcUrl.substring(wcRepoUrl.length());

        // now get the file's info
        info = client.info(file.getPath());

        String fileUrl = info.getUrl();
        String repoUrl = info.getRepository();

        // now we simply erase the repo part of the URL, and the leading path fragment
        File fileRepo = new File(fileUrl.substring(repoUrl.length())); 
        File fileRepoSourcePath = new File(fileRepo.getPath().substring(leadingPathFragment.length()));

        // Get the entire history, with changed files
        LogMessage[] messages =
            client.logMessages(file.getPath(), Revision.START, Revision.BASE, false, true);

        if (messages != null) {
            // reverse the history so we read it from newest to oldest
            // Reversing the Revision params on logMessages()
            // will not give any history at all

            List<LogMessage> messageList = Arrays.asList(messages);
            Collections.reverse(messageList);

            final LinkedHashMap<Long, HistoryEntry> revisions =
                new LinkedHashMap<Long, HistoryEntry>();

            for (LogMessage msg : messageList) {
                HistoryEntry entry = new HistoryEntry();
                entry.setRevision(msg.getRevision().toString());
                entry.setDate(msg.getDate());
                entry.setAuthor(msg.getAuthor());
                entry.setMessage(msg.getMessage());
                entry.setActive(true);
                entry.setRepositoryPath(fileRepo);
                entry.setSourceRootPath(fileRepoSourcePath);

                ChangePath[] changedPaths = msg.getChangedPaths();
                if (changedPaths != null) {
                    for (ChangePath cp : changedPaths) {
                        final String itemPath = cp.getPath();

                        // directory-directory copy
                        if (cp.getAction() == 'A' && cp.getCopySrcPath() != null && fileRepo.getPath().startsWith(itemPath)) {
                            String newRepoLoc = cp.getCopySrcPath() + fileRepo.getPath().substring(itemPath.length());
                            fileRepo = new File(newRepoLoc);                        
                            fileRepoSourcePath = new File(newRepoLoc.substring(leadingPathFragment.length()));
                        }
                        // file-file copy
                        else if (cp.getAction() == 'A' && cp.getCopySrcPath() != null && itemPath.equals(fileRepo)) {
                            fileRepo = new File(cp.getCopySrcPath());
                            fileRepoSourcePath = new File(cp.getCopySrcPath().substring(leadingPathFragment.length()));
                        }
                        if (itemPath.startsWith(leadingPathFragment)) {
                            entry.addFile(itemPath.substring(leadingPathFragment.length()));
                        } else {
                            // This is an arbitrary path which is outside of our working copy.
                            // This link will be broken.
                            entry.addFile(itemPath);
                        }
                    }

                }
                revisions.put(msg.getRevisionNumber(), entry);

            }

            ArrayList<HistoryEntry> entries =
                new ArrayList<HistoryEntry>(revisions.values());

            history.setHistoryEntries(entries);

        }

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
            throws Exception {
        return repository.annotate(file, revision);
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
