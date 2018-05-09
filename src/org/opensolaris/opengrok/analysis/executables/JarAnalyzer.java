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
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis.executables;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.StreamSource;
import org.opensolaris.opengrok.analysis.OGKTextField;
import org.opensolaris.opengrok.search.QueryBuilder;
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

    /**
     * Gets a version number to be used to tag processed documents so that
     * re-analysis can be re-done later if a stored version number is different
     * from the current implementation.
     * @return 20180112_00
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20180112_00; // Edit comment above too!
    }

    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut) throws IOException {
        StringBuilder fout = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(src.getStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String ename = entry.getName();

                if (xrefOut != null) {
                    xrefOut.append("<br/><b>");
                    Util.htmlize(ename, xrefOut);
                    xrefOut.append("</b>");
                }

                /**
                 * If a FULL field exists already, then append to it.
                 */
                useExtantValue(fout, doc, QueryBuilder.FULL);
                fout.append("// ");
                fout.append(ename);
                fout.append("\n");

                /**
                 * Unlike other analyzers, which rely on the full content
                 * existing to be accessed at a file system location identified
                 * by PATH, *.jar and *.class files have virtual content which
                 * is stored here (Store.YES) for analyzer convenience.
                 */
                String fstr = fout.toString();
                doc.add(new OGKTextField(QueryBuilder.FULL, fstr, Store.YES));
                fout.setLength(0);

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

    private static void useExtantValue(StringBuilder accum, Document doc,
        String field) {
        String extantValue = doc.get(field);
        if (extantValue != null) {
            doc.removeFields(field);
            accum.append(extantValue);
        }
    }
}
