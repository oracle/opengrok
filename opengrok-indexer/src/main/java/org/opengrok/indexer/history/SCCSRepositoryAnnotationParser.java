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
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;

/**
 * handles parsing into Annotation object.
 */
public class SCCSRepositoryAnnotationParser implements Executor.StreamHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SCCSRepositoryAnnotationParser.class);

    /**
     * Store annotation created by {@link #processStream(InputStream)}.
     */
    private final Annotation annotation;

    private final Map<String, String> authors;

    private final File file;

    /**
     * Pattern used to extract revision from the {@code sccs get} command.
     */
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("^([\\d.]+)\\s+");

    SCCSRepositoryAnnotationParser(File file, Map<String, String> authors) {
        this.file = file;
        this.annotation = new Annotation(file.getName());
        this.authors = authors;
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
        try (BufferedReader in = new BufferedReader(new InputStreamReader(input))) {
            String line;
            int lineno = 0;
            while ((line = in.readLine()) != null) {
                ++lineno;
                Matcher matcher = ANNOTATION_PATTERN.matcher(line);
                if (matcher.find()) {
                    String rev = matcher.group(1);
                    String author = authors.get(rev);
                    if (author == null) {
                        author = "unknown";
                    }

                    annotation.addLine(rev, author, true);
                } else {
                    LOGGER.log(Level.SEVERE,
                            "Error: did not find annotations in line {0} for ''{2}'': [{1}]",
                            new Object[]{lineno, line, file});
                }
            }
        }
    }
}
