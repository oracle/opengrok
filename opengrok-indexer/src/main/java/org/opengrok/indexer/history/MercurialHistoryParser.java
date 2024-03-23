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
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.InvalidPathException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.util.ForbiddenSymlinkException;

/**
 * Parse a stream of mercurial log comments.
 */
class MercurialHistoryParser implements Executor.StreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MercurialHistoryParser.class);

    /** Prefix which identifies lines with the description of a commit. */
    private static final String DESC_PREFIX = "description: ";

    private List<RepositoryWithHistoryTraversal.ChangesetInfo> entries = new ArrayList<>();
    private final MercurialRepository repository;
    private final String mydir;
    private boolean isDir;
    private final List<ChangesetVisitor> visitors;

    MercurialHistoryParser(MercurialRepository repository, List<ChangesetVisitor> visitors) {
        this.repository = repository;
        this.visitors = visitors;
        mydir = repository.getDirectoryName() + File.separator;
    }

    /**
     * Parse the history for the specified file or directory. If a changeset is
     * specified, only return the history from the changeset right after the specified one.
     *
     * @param file the file or directory to get history for
     * @param sinceRevision the changeset right before the first one to fetch, or
     * {@code null} if all changesets should be fetched
     * @param tillRevision end revision or {@code null}
     * @param numCommits number of revisions to get
     * @throws HistoryException if an error happens when parsing the history
     */
    void parse(File file, String sinceRevision, String tillRevision, Integer numCommits) throws HistoryException {
        isDir = file.isDirectory();
        try {
            Executor executor = repository.getHistoryLogExecutor(file, sinceRevision, tillRevision, false,
                    numCommits);
            int status = executor.exec(true, this);
            if (status != 0) {
                throw new HistoryException(String.format("Failed to get history for '%s' (exit status %d)",
                        file.getAbsolutePath(), status));
            }
        } catch (IOException e) {
            throw new HistoryException(String.format("Failed to get history for '%s'", file.getAbsolutePath()), e);
        }

        // If a changeset to start from is specified, remove that changeset from the list,
        // since only the ones following it should be returned.
        // Also check that the specified changeset was found, otherwise throw an exception.
        if (sinceRevision != null) {
            removeAndVerifyOldestChangeset(entries, sinceRevision);
        }

        // See getHistoryLogExecutor() for explanation.
        if (repository.isHandleRenamedFiles() && file.isFile() && tillRevision != null) {
            removeChangesets(entries, tillRevision);
        }

        // The visitors are fed with the ChangesetInfo instances here (as opposed to in parse()),
        // because of the above manipulations with the entries.
        for (RepositoryWithHistoryTraversal.ChangesetInfo info : entries) {
            for (ChangesetVisitor visitor : visitors) {
                visitor.accept(info);
            }
        }
    }

    /**
     * Remove the oldest changeset from a list (assuming sorted with most recent
     * changeset first) and verify that it is the changeset expected to find there.
     *
     * @param entries a list of {@code HistoryEntry} objects
     * @param revision the revision we expect the oldest entry to have
     * @throws HistoryException if the oldest entry was not the one we expected
     */
    private void removeAndVerifyOldestChangeset(List<RepositoryWithHistoryTraversal.ChangesetInfo> entries, String revision)
            throws HistoryException {

        RepositoryWithHistoryTraversal.ChangesetInfo entry = entries.isEmpty() ? null : entries.remove(entries.size() - 1);

        // TODO We should check more thoroughly that the changeset is the one
        // we expected it to be, since some SCMs may change the revision
        // numbers so that identical revision numbers does not always mean
        // identical changesets. We could for example get the cached changeset
        // and compare more fields, like author and date.
        if (entry == null || !revision.equals(entry.commit.revision)) {
            throw new HistoryException("Cached revision '" + revision
                    + "' not found in the repository "
                    + repository.getDirectoryName());
        }
    }

    private void removeChangesets(List<RepositoryWithHistoryTraversal.ChangesetInfo> entries, String tillRevision) {
        for (Iterator<RepositoryWithHistoryTraversal.ChangesetInfo> iter = entries.listIterator(); iter.hasNext(); ) {
            RepositoryWithHistoryTraversal.ChangesetInfo entry = iter.next();
            if (entry.commit.revision.equals(tillRevision)) {
                break;
            }
            iter.remove();
        }
    }

    /**
     * Process the output from the {@code hg log} command and collect
     * {@link org.opengrok.indexer.history.RepositoryWithHistoryTraversal.ChangesetInfo} elements.
     *
     * @param input The output from the process
     * @throws java.io.IOException If an error occurs while reading the stream
     */
    @Override
    public void processStream(InputStream input) throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        entries = new ArrayList<>();
        String s;
        RepositoryWithHistoryTraversal.ChangesetInfo entry = null;
        while ((s = in.readLine()) != null) {
            if (s.startsWith(MercurialRepository.CHANGESET)) {
                entry = new RepositoryWithHistoryTraversal.ChangesetInfo(new RepositoryWithHistoryTraversal.CommitInfo());
                entries.add(entry);
                entry.commit.revision = s.substring(MercurialRepository.CHANGESET.length()).trim();
            } else if (s.startsWith(MercurialRepository.USER) && entry != null) {
                entry.commit.authorName = s.substring(MercurialRepository.USER.length()).trim();
            } else if (s.startsWith(MercurialRepository.DATE) && entry != null) {
                Date date;
                try {
                    date = repository.parse(s.substring(MercurialRepository.DATE.length()).trim());
                } catch (ParseException pe) {
                    //
                    // Overriding processStream() thus need to comply with the
                    // set of exceptions it can throw.
                    //
                    throw new IOException("Could not parse date: " + s, pe);
                }
                entry.commit.date = date;
            } else if (s.startsWith(MercurialRepository.FILES) && entry != null) {
                String[] strings = s.split(" ");
                for (int ii = 1; ii < strings.length; ++ii) {
                    if (strings[ii].length() > 0) {
                        File f = new File(mydir, strings[ii]);
                        try {
                            String path = env.getPathRelativeToSourceRoot(f);
                            entry.files.add(path.intern());
                        } catch (ForbiddenSymlinkException e) {
                            LOGGER.log(Level.FINER, e.getMessage());
                            // ignore
                        } catch (FileNotFoundException e) { // NOPMD
                            // If the file is not located under the source root,
                            // ignore it (bug #11664).
                        } catch (InvalidPathException e) {
                            LOGGER.log(Level.WARNING, e.getMessage());
                        }
                    }
                }
            } else if (repository.isHandleRenamedFiles() && s.startsWith(MercurialRepository.FILE_COPIES) &&
                entry != null && isDir) {
                /*
                 * 'file_copies:' should be present only for directories but
                 * we use isDir to be on the safe side.
                 */
                s = s.replaceFirst(MercurialRepository.FILE_COPIES, "");
                String[] splitArray = s.split("\\)");
                for (String part: splitArray) {
                     /*
                      * This will fail for file names containing ' ('.
                      */
                     String[] move = part.split(" \\(");
                     File f = new File(mydir + move[0]);
                     if (!move[0].isEmpty() && f.exists()) {
                             entry.renamedFiles.add(repository.getDirectoryNameRelative() + File.separator + move[0]);
                     }
                }
            } else if (s.startsWith(DESC_PREFIX) && entry != null) {
                entry.commit.message = decodeDescription(s);
            } else if (s.equals(MercurialRepository.END_OF_ENTRY)
                && entry != null) {
                    entry = null;
            } else if (s.length() > 0) {
                LOGGER.log(Level.WARNING,
                    "Invalid/unexpected output {0} from hg log for repo {1}",
                    new Object[]{s, repository.getDirectoryName()});
            }
        }
    }

    /**
     * Decode a line with a description of a commit. The line is a sequence of
     * XML character entities that need to be converted to single characters.
     * This is to prevent problems if the log message contains one of the
     * prefixes that {@link #processStream(InputStream)} is looking for (bug
     * #405).
     *
     * This method is way too tolerant, and won't complain if the line has
     * a different format than expected. It will return weird results, though.
     *
     * @param line the XML encoded line
     * @return the decoded description
     */
    private String decodeDescription(String line) {
        StringBuilder out = new StringBuilder();
        int value = 0;

        // fetch the char values from the &#ddd; sequences
        for (int i = DESC_PREFIX.length(); i < line.length(); i++) {
            char ch = line.charAt(i);
            if (Character.isDigit(ch)) {
                value = value * 10 + Character.getNumericValue(ch);
            } else if (ch == ';') {
                out.append((char) value);
                value = 0;
            }
        }

        assert value == 0 : "description did not end with a semi-colon";

        return out.toString();
    }
}
