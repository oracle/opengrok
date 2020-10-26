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
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import org.opengrok.indexer.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.opengrok.indexer.util.CustomAssertions.assertLinesEqual;
import static org.opengrok.indexer.util.StreamUtils.readerFromResource;

/**
 * Represents an abstract base class for language-specific xref test classes.
 */
public abstract class XrefTestBase {

    /**
     * Tests the XREF result of a specified factory and arguments.
     * @param factory a defined instance
     * @param sourceResource a defined resource name for source code
     * @param resultResource a defined resource name for expected XREF result
     * @param defs an optional instance
     * @param expectedLOC the number of expected LOC
     * @throws IOException thrown if an I/O error occurs
     */
    protected void writeAndCompare(
            AnalyzerFactory factory, String sourceResource,
            String resultResource, Definitions defs, int expectedLOC)
            throws IOException {

        try (Reader sourceRes = readerFromResource(sourceResource);
             Reader resultRes = readerFromResource(resultResource)) {
            writeAndCompare(factory, sourceRes, resultRes, defs, expectedLOC);
        }
    }

    /**
     * Tests the XREF result of a specified factory and arguments.
     * @param factory a defined instance
     * @param source a defined instance for source code
     * @param result a defined instance for expected XREF result
     * @param defs an optional instance
     * @param expectedLOC the number of expected LOC
     * @throws IOException thrown if an I/O error occurs
     */
    protected void writeAndCompare(
            AnalyzerFactory factory, Reader source, Reader result,
            Definitions defs, int expectedLOC) throws IOException {

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

        int actLOC = writeXref(new PrintStream(outBytes), factory, source, defs);
        outBytes.close();
        String outStr = new String(outBytes.toByteArray(), StandardCharsets.UTF_8);
        String[] gotten = outStr.split("\n");

        String expStr = StreamUtils.readToEnd(result);
        String[] expected = expStr.split("\n");

        String messagePrefix = factory.getClass().getName();
        assertLinesEqual(messagePrefix + " xref", expected, gotten);
        assertEquals(messagePrefix + " LOC", expectedLOC, actLOC);
    }

    private int writeXref(
            PrintStream oss, AnalyzerFactory factory, Reader in,
            Definitions defs) throws IOException {

        oss.print(getHtmlBegin());

        Writer out = new StringWriter();
        AbstractAnalyzer analyzer = factory.getAnalyzer();
        analyzer.setScopesEnabled(true);
        analyzer.setFoldingEnabled(true);

        WriteXrefArgs writeArgs = new WriteXrefArgs(in, out);
        writeArgs.setDefs(defs);

        Xrefer xref = analyzer.writeXref(writeArgs);
        oss.print(out.toString());

        oss.print(getHtmlEnd());
        return xref.getLOC();
    }

    /**
     * Subclasses can override if the XREF is non-standard.
     * @return default HTML document header
     */
    protected String getHtmlBegin() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<title>sampleFile - OpenGrok cross reference" +
                " for /sampleFile</title></head><body>\n";
    }

    /**
     * Subclasses can override if the XREF is non-standard.
     * @return default HTML document footer
     */
    protected String getHtmlEnd() {
        return "</body>\n" +
                "</html>\n";
    }
}
