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
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.web.Util;

/**
 * handles parsing the output of the {@code hg annotate} command
 * into an annotation object.
 */
class MercurialAnnotationParser implements Executor.StreamHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MercurialAnnotationParser.class);

    private Annotation annotation = null;
    HashMap<String, HistoryEntry> revs;
    File file;

    /**
     * Pattern used to extract author/revision from {@code hg annotate}.
     */
    private static final Pattern ANNOTATION_PATTERN
            = Pattern.compile("^\\s*(\\d+):");

    MercurialAnnotationParser(File file, HashMap<String, HistoryEntry> revs) {
        this.file = file;
        this.revs = revs;
    }

    @Override
    public void processStream(InputStream input) throws IOException {
        annotation = new Annotation(file.getName());
        String line;
        int lineno = 0;
        Matcher matcher = ANNOTATION_PATTERN.matcher("");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(input))) {
            while ((line = in.readLine()) != null) {
                ++lineno;
                matcher.reset(line);
                if (matcher.find()) {
                    String rev = matcher.group(1);
                    String author = "N/A";
                    // Use the history index hash map to get the author.
                    if (revs.get(rev) != null) {
                        author = revs.get(rev).getAuthor();
                    }
                    annotation.addLine(rev, Util.getEmail(author.trim()), true);
                } else {
                    LOGGER.log(Level.SEVERE,
                            "Error: did not find annotation in line {0}: [{1}]",
                            new Object[]{lineno, line});
                }
            }
        }
    }

    Annotation getAnnotation() {
        return this.annotation;
    }
}
