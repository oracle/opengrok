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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

/**
 * handles parsing the output of the {@code svn annotate}
 * command into an annotation object.
 */
public class SubversionAnnotationParser implements Executor.StreamHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubversionAnnotationParser.class);

    /**
     * Store annotation created by processStream.
     */
    private final Annotation annotation;

    private final String fileName;

    /**
     * @param fileName the name of the file being annotated
     */
    public SubversionAnnotationParser(String fileName) {
        annotation = new Annotation(fileName);
        this.fileName = fileName;
    }

    /**
     * Returns the annotation that has been created.
     *
     * @return annotation an annotation object
     */
    public Annotation getAnnotation() {
        return annotation;
    }

    @Override
    public void processStream(InputStream input) throws IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser;
        try {
            saxParser = factory.newSAXParser();
            saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, ""); // Compliant
            saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); // compliant
        } catch (ParserConfigurationException | SAXException ex) {
            IOException err = new IOException("Failed to create SAX parser", ex);
            throw err;
        }

        AnnotateHandler handler = new AnnotateHandler(fileName, annotation);
        try (BufferedInputStream in
                = new BufferedInputStream(input)) {
            saxParser.parse(in, handler);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "An error occurred while parsing the xml output", e);
        }
    }

    private static class AnnotateHandler extends DefaultHandler2 {

        String rev;
        String author;
        final Annotation annotation;
        final StringBuilder sb;

        AnnotateHandler(String filename, Annotation annotation) {
            this.annotation = annotation;
            sb = new StringBuilder();
        }

        @Override
        public void startElement(String uri, String localName, String qname,
                Attributes attr) {
            sb.setLength(0);
            if (null != qname) {
                switch (qname) {
                    case "entry":
                        rev = null;
                        author = null;
                        break;
                    case "commit":
                        rev = attr.getValue("revision");
                        break;
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qname) {
            if (null != qname) {
                switch (qname) {
                    case "author":
                        author = sb.toString();
                        break;
                    case "entry":
                        annotation.addLine(rev, author, true);
                        break;
                }
            }
        }

        @Override
        public void characters(char[] arg0, int arg1, int arg2) {
            sb.append(arg0, arg1, arg2);
        }
    }
}
