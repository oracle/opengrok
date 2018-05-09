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
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis.archive;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import org.apache.lucene.document.Document;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.IteratorReader;
import org.opensolaris.opengrok.analysis.StreamSource;
import org.opensolaris.opengrok.analysis.OGKTextField;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.web.Util;

/**
 * Analyzes TAR files Created on September 22, 2005
 *
 * @author Chandan
 */
public class TarAnalyzer extends FileAnalyzer {

    protected TarAnalyzer(FileAnalyzerFactory factory) {
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
        ArrayList<String> names = new ArrayList<>();

        try (TarInputStream zis = new TarInputStream(src.getStream())) {
            TarEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                names.add(name);
                if (xrefOut != null) {
                    Util.htmlize(name, xrefOut);
                    xrefOut.append("<br/>");
                }
            }
        }

        doc.add(new OGKTextField(QueryBuilder.FULL, new IteratorReader(names)));
    }
}
