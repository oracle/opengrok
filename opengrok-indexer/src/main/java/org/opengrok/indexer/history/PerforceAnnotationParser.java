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
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;

/**
 * handles parsing the output of the {@code p4 annotate} command
 * into an annotation object.
 */
public class PerforceAnnotationParser implements Executor.StreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerforceAnnotationParser.class);

    /**
     * Store annotation created by processStream.
     */
    private final Annotation annotation;

    private final PerforceRepository repo;

    private final File file;

    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("^(\\d+): .*");

    /**
     * @param repo defined instance
     * @param file the file being annotated
     */
    public PerforceAnnotationParser(PerforceRepository repo, File file) {
        annotation = new Annotation(file.getName());
        this.repo = repo;
        this.file = file;
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
        // Pass null for revision to get all history for the file.
        PerforceHistoryParser parser = new PerforceHistoryParser(repo);
        List<HistoryEntry> revisions = parser.getRevisions(file, null).getHistoryEntries();
        HashMap<String, String> revAuthor = new HashMap<>();
        for (HistoryEntry entry : revisions) {
            revAuthor.put(entry.getRevision(), entry.getAuthor());
        }

        String line;
        int lineno = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            while ((line = reader.readLine()) != null) {
                ++lineno;
                if (line.equals("(... files differ ...)")) {
                    // Perforce knows as a binary file?
                    continue;
                }

                Matcher matcher = ANNOTATION_PATTERN.matcher(line);
                if (matcher.find()) {
                    String revision = matcher.group(1);
                    String author = revAuthor.get(revision);
                    annotation.addLine(revision, author, true);
                } else {
                    LOGGER.log(Level.WARNING,
                            "Error: did not find annotation in line {0} for ''{1}'': [{2}]",
                            new Object[]{lineno, this.file, line});
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    String.format("Error: Could not read annotations for '%s'", this.file), e);
        }
    }
}
