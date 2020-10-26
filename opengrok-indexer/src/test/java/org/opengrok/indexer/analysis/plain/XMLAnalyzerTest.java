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
 * Copyright (c) 2008, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.plain;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.Test;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.WriteXrefArgs;

import static org.junit.Assert.assertTrue;

public class XMLAnalyzerTest {
    @Test
    public void bug2225() throws IOException {
        String xmlText =
                "<?xml version=\"1.0\" encoding=\"US-ASCII\"?>\n" +
                "<foo>\n" +
                "  <bar name=\"com.foo.bar.MyClass\"/>\n" +
                "  <bar name=\"README.txt\"/>\n" +
                "</foo>";
        StringReader sr = new StringReader(xmlText);
        StringWriter sw = new StringWriter();
        XMLAnalyzerFactory fac = new XMLAnalyzerFactory();
        AbstractAnalyzer analyzer = fac.getAnalyzer();
        analyzer.writeXref(new WriteXrefArgs(sr, sw));
        String[] xref = sw.toString().split("\n");
        // Reference to a Java class should have / instead of . in the path
        assertTrue(xref[2].contains("path=com/foo/bar/MyClass"));
        // Ordinary file names should not have .'s replaced
        assertTrue(xref[3].contains("path=README.txt"));
    }
    
    @Test
    public void bug806() throws IOException {
        String xmlText
                = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<server>\n"
                + "  <mbean code=\"QuartzBean\" \n"
                + "      name=\"user:service=QuartzService,name=QuartzService\">\n"
                + "    <depends>jboss.jca:service=DataSourceBinding,name=ServerDS</depends>\n"
                + "    <attribute name=\"JndiName\">Quartz</attribute>\n"
                + "    <attribute name=\"Properties\">\n"
                + "      org.quartz.plugin.jobInitializer.fileNames = ../server/default/conf/scheduler/quartz_jobs.xml\n"
                + "    </attribute>\n"
                + "  </mbean>\n"
                + "</server>";
        StringReader sr = new StringReader(xmlText);
        StringWriter sw = new StringWriter();
        XMLAnalyzerFactory fac = new XMLAnalyzerFactory();
        AbstractAnalyzer analyzer = fac.getAnalyzer();
        analyzer.writeXref(new WriteXrefArgs(sr, sw));
        String[] xref = sw.toString().split("\n");
        // don't remove ../
        assertTrue(xref[7].contains("org.quartz.plugin.jobInitializer.fileNames</a> = <a href=\"/source/s?path=../\">..</a>/"));
    }

    /**
     * XML special chars inside a string were not escaped if single quotes
     * were used around the string. Bug #15859.
     * @throws IOException I/O exception
     */
    @Test
    public void xrefWithSpecialCharsInStringLiterals() throws IOException {
        StringReader input =
                new StringReader("<foo xyz='<betweensinglequotes>'> </foo>");
        StringWriter output = new StringWriter();
        XMLAnalyzerFactory fac = new XMLAnalyzerFactory();
        AbstractAnalyzer analyzer = fac.getAnalyzer();
        analyzer.writeXref(new WriteXrefArgs(input, output));
        assertTrue(output.toString().contains("&lt;betweensinglequotes&gt;"));

        input = new StringReader("<foo xyz=\"<betweendoublequotes>\"> </foo>");
        output = new StringWriter();
        analyzer.writeXref(new WriteXrefArgs(input, output));
        assertTrue(output.toString().contains("&lt;betweendoublequotes&gt;"));
    }
}
