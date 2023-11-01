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
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2020, 2023, Ric Harris <harrisric@users.noreply.github.com>.
 */
package org.opengrok.indexer.history;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Parse source history for a Subversion Repository.
 *
 * @author Trond Norbye
 */
class SubversionHistoryParser implements Executor.StreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubversionHistoryParser.class);

    private SAXParser saxParser = null;
    private Handler handler;

    private static class Handler extends DefaultHandler2 {

        private static final String COPYFROM_PATH = "copyfrom-path";

        /**
         * Example of the longest date format that we should accept - SimpleDateFormat cannot cope with micro/nano seconds.
         */
        static final int SVN_MILLIS_DATE_LENGTH = "2020-03-26T15:38:55.999Z".length();

        final String prefix;
        final String home;
        final int length;
        final List<HistoryEntry> entries = new ArrayList<>();
        final Set<String> renamedFiles = new HashSet<>();
        final Map<String, String> renamedToDirectoryRevisions = new HashMap<>();
        final SubversionRepository repository;
        HistoryEntry entry;
        StringBuilder sb;
        boolean isRenamedFile;
        boolean isRenamedDir;

        Handler(String home, String prefix, int length, SubversionRepository repository) {
            this.home = home;
            this.prefix = prefix;
            this.length = length;
            this.repository = repository;
            sb = new StringBuilder();
        }

        Set<String> getRenamedFiles() {
            return renamedFiles;
        }

        Map<String, String> getRenamedDirectories() {
          return renamedToDirectoryRevisions;
        }

        @Override
        public void startElement(String uri, String localName, String qname, Attributes attr) {
            isRenamedFile = false;
            isRenamedDir = false;
            if ("logentry".equals(qname)) {
                entry = new HistoryEntry();
                entry.setActive(true);
                entry.setRevision(attr.getValue("revision"));
            } else if ("path".equals(qname) && attr.getIndex(COPYFROM_PATH) != -1) {
              LOGGER.log(Level.FINER, "rename for {0}", attr.getValue(COPYFROM_PATH));
              if ("dir".equals(attr.getValue("kind"))) {
                isRenamedDir = true;
              } else {
                isRenamedFile = true;
              }
            }
            sb.setLength(0);
        }

        @Override
        public void endElement(String uri, String localName, String qname) throws SAXException {
            String s = sb.toString();
            if ("author".equals(qname)) {
                entry.setAuthor(s);
            } else if ("date".equals(qname)) {
                try {
                    // need to strip microseconds off - assume final character is Z otherwise invalid anyway.
                    String dateString = s;
                    if (s.length() > SVN_MILLIS_DATE_LENGTH) {
                      dateString = dateString.substring(0, SVN_MILLIS_DATE_LENGTH - 1) +
                          dateString.charAt(dateString.length() - 1);
                    }
                    entry.setDate(repository.parse(dateString));
                } catch (ParseException ex) {
                    throw new SAXException("Failed to parse date: " + s, ex);
                }
            } else if ("path".equals(qname)) {
                /*
                 * We only want valid files in the repository, not the
                 * top-level directory itself, hence the check for inequality.
                 */
                if (s.startsWith(prefix) && !s.equals(prefix)) {
                    File file = new File(home, s.substring(prefix.length()));
                    String path = file.getAbsolutePath().substring(length);
                    // The same file names may be repeated in many commits,
                    // so intern them to reduce the memory footprint.
                    entry.addFile(path.intern());
                    if (isRenamedFile) {
                        renamedFiles.add(path.intern());
                    }
                    if (isRenamedDir) {
                      renamedToDirectoryRevisions.put(path.intern(), entry.getRevision());
                    }
                } else {
                    LOGGER.log(Level.FINER, "Skipping file ''{0}'' outside repository ''{1}''",
                            new Object[]{s, this.home});
                }
            } else if ("msg".equals(qname)) {
                entry.setMessage(s);
            }
            if ("logentry".equals(qname)) {
                entries.add(entry);
            }
            sb.setLength(0);
        }

        @Override
        public void characters(char[] arg0, int arg1, int arg2) {
            sb.append(arg0, arg1, arg2);
        }
    }

    /**
     * Initialize the SAX parser instance.
     */
    private void initSaxParser() throws HistoryException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        saxParser = null;
        try {
            saxParser = factory.newSAXParser();
            saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, ""); // Compliant
            saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); // compliant
        } catch (Exception ex) {
            throw new HistoryException("Failed to create SAX parser", ex);
        }
    }

    /**
     * Parse the history for the specified file.
     *
     * @param file the file to parse history for
     * @param repos Pointer to the SubversionRepository
     * @param sinceRevision the revision number immediately preceding the first
     * revision we want, or {@code null} to fetch the entire history
     * @return object representing the file's history
     */
    History parse(File file, SubversionRepository repos, String sinceRevision,
            int numEntries, CommandTimeoutType cmdType)
            throws HistoryException {

        initSaxParser();
        handler = new Handler(repos.getDirectoryName(), repos.reposPath,
                RuntimeEnvironment.getInstance().getSourceRootPath().length(),
                repos);

        Executor executor;
        try {
            executor = repos.getHistoryLogExecutor(file, sinceRevision,
                    numEntries, cmdType);
        } catch (IOException e) {
            throw new HistoryException("Failed to get history for: \"" +
                    file.getAbsolutePath() + "\"", e);
        }

        int status = executor.exec(true, this);
        if (status != 0) {
            throw new HistoryException("Failed to get history for: \"" +
                    file.getAbsolutePath() + "\" Exit code: " + status);
        }

        List<HistoryEntry> entries = handler.entries;

        // If we only fetch parts of the history, we're not interested in
        // sinceRevision. Remove it.
        if (sinceRevision != null) {
            repos.removeAndVerifyOldestChangeset(entries, sinceRevision);
        }

        Set<String> allRenamedFiles = findRenamedFilesFromDirectories(handler.getRenamedDirectories(), repos, cmdType);
        allRenamedFiles.addAll(handler.getRenamedFiles());

        return new History(entries, new ArrayList<>(allRenamedFiles));
    }

    /**
     * @return a set of files that were present in the directories at the given revisions.
     */
    private Set<String> findRenamedFilesFromDirectories(Map<String, String> renamedDirectories, SubversionRepository repos,
       CommandTimeoutType cmdType) {

      Set<String> renamedFiles = new HashSet<>();
      for (Entry<String, String> entry : renamedDirectories.entrySet()) {
        renamedFiles.addAll(repos.getFilesInDirectoryAtRevision(entry.getKey(), entry.getValue(), cmdType));
      }
      return renamedFiles;
    }

    /**
     * Process the output from the log command and insert the HistoryEntries
     * into the history field.
     *
     * @param input The output from the process
     */
    @Override
    public void processStream(InputStream input) throws IOException {
        try {
            initSaxParser();
            saxParser.parse(new BufferedInputStream(input), handler);
        } catch (Exception e) {
            throw new IOException("An error occurred while parsing the xml output", e);
        }
    }

    /**
     * Parse the given string.
     *
     * @param buffer The string to be parsed
     * @return The parsed history
     * @throws IOException if we fail to parse the buffer
     */
    History parse(String buffer) throws IOException {
        handler = new Handler("/", "", 0, new SubversionRepository());
        processStream(new ByteArrayInputStream(buffer.getBytes(StandardCharsets.UTF_8)));
        return new History(handler.entries, handler.getRenamedFiles());
    }
}
