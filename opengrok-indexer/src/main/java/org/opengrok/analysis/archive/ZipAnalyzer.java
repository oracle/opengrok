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
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.analysis.archive;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.lucene.document.Document;
import org.opengrok.analysis.FileAnalyzer;
import org.opengrok.analysis.FileAnalyzerFactory;
import org.opengrok.analysis.IteratorReader;
import org.opengrok.analysis.StreamSource;
import org.opengrok.analysis.OGKTextField;
import org.opengrok.search.QueryBuilder;
import org.opengrok.web.Util;

/**
 * Analyzes Zip files Created on September 22, 2005
 *
 * @author Chandan
 */
public class ZipAnalyzer extends FileAnalyzer {

    protected ZipAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
    }

    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut) throws IOException {
        ArrayList<String> names = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(src.getStream())) {
            ZipEntry entry;
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
