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
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.web.Util;

/**
 * Handles parsing the output of the {@code hg annotate} command
 * into an {@link Annotation} object.
 */
class MercurialAnnotationParser implements Executor.StreamHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MercurialAnnotationParser.class);

    private Annotation annotation = null;
    private final HashMap<String, HistoryEntry> revs;
    private final File file;

    /**
     * Pattern used to extract author/revision from the {@code hg annotate} command.
     * Obviously, this has to be in concordance with the output template used by
     * {@link MercurialRepository#annotate(File, String)}.
     */
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("^(\\d+)\\t([0-9a-f]+):");

    MercurialAnnotationParser(File file, @NotNull HashMap<String, HistoryEntry> revs) {
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
                    String displayRev = matcher.group(1);
                    String fullRev = displayRev + ":" + matcher.group(2);
                    // Use the history index hash map to get the author.
                    String author = Optional.ofNullable(revs.get(fullRev)).map(HistoryEntry::getAuthor).
                            orElse("N/A");
                    // TODO: add check that history stored in the index uses the same format of the revision
                    annotation.addLine(fullRev, Util.getEmail(author.trim()), true, displayRev);
                } else {
                    LOGGER.log(Level.WARNING,
                            "Error: did not find annotation in line {0} for ''{1}'': [{2}]",
                            new Object[]{lineno, this.file, line});
                }
            }
        }
    }

    Annotation getAnnotation() {
        return this.annotation;
    }
}
