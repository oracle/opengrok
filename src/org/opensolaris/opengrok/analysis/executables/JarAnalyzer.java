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
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis.executables;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.StreamSource;
import org.opensolaris.opengrok.web.Util;

/**
 * Analyzes JAR, WAR, EAR (Java Archive) files. Created on September 22, 2005
 *
 * @author Chandan
 */
public class JarAnalyzer extends FileAnalyzer {

    protected JarAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
    }

    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(src.getStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String ename = entry.getName();

                if (xrefOut != null) {
                    xrefOut.append("<br/><b>");
                    Util.htmlize(ename, xrefOut);
                    xrefOut.append("</b>");
                }

                doc.add(new TextField("full", ename, Store.NO));
                FileAnalyzerFactory fac = AnalyzerGuru.find(ename);
                if (fac instanceof JavaClassAnalyzerFactory) {
                    if (xrefOut != null) {
                        xrefOut.append("<br/>");
                    }
                    JavaClassAnalyzer jca =
                            (JavaClassAnalyzer) fac.getAnalyzer();
                    jca.analyze(doc, new BufferedInputStream(zis), xrefOut);
                }
            }
        }
    }
}
