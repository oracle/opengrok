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
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.Executor;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Parse source history for a Subversion Repository
 *
 * @author Trond Norbye
 */
class SubversionHistoryParser implements HistoryParser, Executor.StreamHandler {

    private History history;
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

        Handler(String home, String prefix, int length, DateFormat df) {
            this.home = home;
            this.prefix = prefix;
            this.length = length;
            format = df;
            sb = new StringBuilder();
        }

        @Override
        public void startElement(String uri, String localName, String qname, Attributes attr) throws SAXException {
            if ("logentry".equals(qname)) {
                entry = new HistoryEntry();
                entry.setActive(true);
                entry.setRevision(attr.getValue("revision"));
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
                    entry.setDate(format.parse(s));
                } catch (ParseException ex) {
                    OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to parse: " + s, ex);
                }
            } else if ("path".equals(qname)) {
                if (s.startsWith(prefix)) {
                    File file = new File(home, s.substring(prefix.length()));
                    String path = file.getAbsolutePath().substring(length);
                    entry.addFile(path.intern());
                } else {
                    OpenGrokLogger.getLogger().log(Level.FINE, "Skipping file outside repository: " + s);
                }
            } else if ("msg".equals(qname)) {
                entry.setMessage(s);
            } if ("logentry".equals(qname)) {
                entries.add(entry);
            }
            sb.setLength(0);
        }

        @Override
        public void characters(char[] arg0, int arg1, int arg2) throws SAXException {
            sb.append(arg0, arg1, arg2);
        }
    }

    /**
     * Initialize the SAX parser instance.
     */
    private void initSaxParser() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        saxParser = null;
        try {
            saxParser = factory.newSAXParser();
        } catch (ParserConfigurationException ex) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to create SAX parser", ex);
        } catch (SAXException ex) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to create SAX parser", ex);
        }
    }

    /**
     * Parse the history for the specified file.
     *
     * @param file the file to parse history for
     * @param repos Pointer to the SubversionReporitory
     * @return object representing the file's history
     */
    public History parse(File file, Repository repos) throws HistoryException {
        initSaxParser();
        handler = new Handler(repos.getDirectoryName(), 
                ((SubversionRepository) repos).reposPath, 
                RuntimeEnvironment.getInstance().getSourceRootPath().length(),
                repos.getDateFormat());
        
        if (saxParser == null) {
            throw new HistoryException("Failed to create SAX parser");
        }

        Executor executor = ((SubversionRepository) repos).getHistoryLogExecutor(file);
        int status = executor.exec(true, this);

        if (status != 0) {
            throw new HistoryException("Failed to get history for: \"" +
                    file.getAbsolutePath() + "\" Exit code: " + status);
        }

        return history;
    }
            
   /**
     * Process the output from the log command and insert the HistoryEntries
     * into the history field.
     *
     * @param input The output from the process
     * @throws java.io.IOException If an error occurs while reading the stream
     */
    public void processStream(InputStream input) throws IOException {
        try {
            initSaxParser();
            history = new History();
            saxParser.parse(new BufferedInputStream(input), handler);
            history.setHistoryEntries(handler.entries);
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
    public History parse(String buffer) throws IOException {
        handler = new Handler("/", "", 0, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US));
        processStream(new ByteArrayInputStream(buffer.getBytes("UTF-8")));
        return history;
    }
}
