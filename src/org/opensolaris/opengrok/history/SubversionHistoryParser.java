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
 * Copyright (c) 2006, 2010, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.util.Interner;
import org.xml.sax.Attributes;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Parse source history for a Subversion Repository
 *
 * @author Trond Norbye
 */
class SubversionHistoryParser implements Executor.StreamHandler {

    private SAXParser saxParser = null;
    private Handler handler;

    private static class Handler extends DefaultHandler2 {

        final String prefix;
        final String home;
        final int length;
        final List<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        final DateFormat format;
        HistoryEntry entry;
        StringBuilder sb;
        private final Interner<String> stringInterner = new Interner<String>();

        Handler(String home, String prefix, int length, DateFormat df) {
            this.home = home;
            this.prefix = prefix;
            this.length = length;
            format = df;
            sb = new StringBuilder();
        }

        @Override
        public void startElement(String uri, String localName, String qname, Attributes attr) {
            if ("logentry".equals(qname)) {
                entry = new HistoryEntry();
                entry.setActive(true);
                entry.setRevision(attr.getValue("revision"));
            }
            sb.setLength(0);
        }

        @Override
        public void endElement(String uri, String localName, String qname) {
            String s = sb.toString();
            if ("author".equals(qname)) {
                entry.setAuthor(s);
            } else if ("date".equals(qname)) {
                try {
                    entry.setDate(format.parse(s));
                } catch (ParseException ex) {
                    OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to parse: " + s, ex);
                }
            } else if ("path".equals(qname)) {
                if (s.startsWith(prefix)) {
                    File file = new File(home, s.substring(prefix.length()));
                    String path = file.getAbsolutePath().substring(length);
                    // The same file names may be repeated in many commits,
                    // so intern them to reduce the memory footprint.
                    // Bug #15956: Don't use String.intern(), since that may
                    // exhaust the permgen space. Instead, use our own
                    // interner that allocates space on the heap.
                    path = stringInterner.intern(path);
                    entry.addFile(path);
                } else {
                    OpenGrokLogger.getLogger().log(Level.INFO, "Skipping file outside repository: " + s);
                }
            } else if ("msg".equals(qname)) {
                entry.setMessage(s);
            } if ("logentry".equals(qname)) {
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
        } catch (Exception ex) {
            throw new HistoryException("Failed to create SAX parser", ex);
        }
    }

    /**
     * Parse the history for the specified file.
     *
     * @param file the file to parse history for
     * @param repos Pointer to the SubversionReporitory
     * @param sinceRevision the revision number immediately preceding the first
     * revision we want, or {@code null} to fetch the entire history
     * @return object representing the file's history
     */
    History parse(File file, SubversionRepository repos, String sinceRevision)
            throws HistoryException {
        initSaxParser();
        handler = new Handler(repos.getDirectoryName(), repos.reposPath, 
                RuntimeEnvironment.getInstance().getSourceRootPath().length(),
                repos.getDateFormat());
        
        Executor executor = repos.getHistoryLogExecutor(file, sinceRevision);
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

        return new History(entries);
    }

   /**
     * Process the output from the log command and insert the HistoryEntries
     * into the history field.
     *
     * @param input The output from the process
     */
    @Override
    public void processStream(InputStream input) {
        try {
            initSaxParser();
            saxParser.parse(new BufferedInputStream(input), handler);
        } catch (Exception e) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, "An error occurred while parsing the xml output", e);
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
        handler = new Handler("/", "", 0, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()));
        processStream(new ByteArrayInputStream(buffer.getBytes("UTF-8")));
        return new History(handler.entries);
    }
}
