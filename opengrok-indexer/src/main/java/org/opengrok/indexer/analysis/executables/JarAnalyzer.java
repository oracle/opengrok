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
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.executables;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.analysis.FileAnalyzer;
import org.opengrok.indexer.analysis.OGKTextField;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.web.Util;

/**
 * Analyzes JAR, WAR, EAR (Java Archive) files. Created on September 22, 2005
 *
 * @author Chandan
 */
public class JarAnalyzer extends FileAnalyzer {

    private static final String[] FIELD_NAMES = new String[]
            {QueryBuilder.FULL, QueryBuilder.REFS, QueryBuilder.DEFS};

    protected JarAnalyzer(AnalyzerFactory factory) {
        super(factory);
    }

    /**
     * @return {@code null} as there is no aligned language
     */
    @Override
    public String getCtagsLang() {
        return null;
    }

    /**
     * Gets a version number to be used to tag processed documents so that
     * re-analysis can be re-done later if a stored version number is different
     * from the current implementation.
     * @return 20180612_00
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20180612_00; // Edit comment above too!
    }

    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut) throws IOException {
        JFieldBuilder jfbuilder = new JFieldBuilder();
        try (ZipInputStream zis = new ZipInputStream(src.getStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String ename = entry.getName();

                if (xrefOut != null) {
                    xrefOut.append("<br/><b>");
                    Util.htmlize(ename, xrefOut);
                    xrefOut.append("</b>");
                }

                StringWriter fout = jfbuilder.write(QueryBuilder.FULL);
                fout.write(ename);
                fout.write("\n");

                AnalyzerFactory fac = AnalyzerGuru.find(ename);
                if (fac instanceof JavaClassAnalyzerFactory) {
                    if (xrefOut != null) {
                        xrefOut.append("<br/>");
                    }
                    JavaClassAnalyzer jca =
                            (JavaClassAnalyzer) fac.getAnalyzer();
                    jca.analyze(doc, new BufferedInputStream(zis), xrefOut,
                            jfbuilder);
                }
            }
        }

        for (String name : FIELD_NAMES) {
            if (jfbuilder.hasField(name)) {
                String fstr = jfbuilder.write(name).toString();
                doc.add(new OGKTextField(name, fstr, Store.NO));
            }
        }
    }
}
