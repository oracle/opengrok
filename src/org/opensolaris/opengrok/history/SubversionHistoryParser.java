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
import java.io.File;
import java.io.IOException;
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
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Parse source history for a Subversion Repository
 *
 * @author Trond Norbye
 */
class SubversionHistoryParser implements HistoryParser {

    private static class Handler extends DefaultHandler2 {

        final String prefix;
        final String home;
        final int length;
        final List<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        HistoryEntry entry;
        StringBuilder sb;

        Handler(final SubversionRepository repos) {
            this.home = repos.getDirectoryName();
            this.prefix = repos.reposPath;
            this.length = RuntimeEnvironment.getInstance().getSourceRootPath().length();
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
     * Parse the history for the specified file.
     *
     * @param file the file to parse history for
     * @param repos Pointer to the SubversionReporitory
     * @return object representing the file's history
     */
    public History parse(File file, Repository repos)
            throws IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = null;
        try {
            saxParser = factory.newSAXParser();
        } catch (ParserConfigurationException ex) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to create SAX parser", ex);
        } catch (SAXException ex) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to create SAX parser", ex);
        }
        if (saxParser == null) {
            return null;
        }

        assert (repos instanceof SubversionRepository);
        SubversionRepository srepos = (SubversionRepository) repos;
        History history = null;

        Process process = null;
        BufferedInputStream in = null;
        try {
            process = srepos.getHistoryLogProcess(file);
            if (process == null) {
                return null;
            }
            in = new BufferedInputStream(process.getInputStream());
            Handler handler = new Handler(srepos);
            try {
                saxParser.parse(in, handler);
                history = new History();
                history.setHistoryEntries(handler.entries);
            } catch (Exception e) {
                OpenGrokLogger.getLogger().log(Level.SEVERE, "An error occurred while parsing the xml output", e);
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException exp) {
                    // Ignore..
                }
            }

            if (process != null) {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException exp) {
                    // the process is still running??? just kill it..
                    process.destroy();
                }
            }
        }

        return history;
    }
}
