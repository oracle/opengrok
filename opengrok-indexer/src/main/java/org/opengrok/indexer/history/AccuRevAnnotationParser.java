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
 * Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;

/**
 * handles parsing the output of the {@code accurev annotate} command
 * into an annotation object.
 */
public class AccuRevAnnotationParser implements Executor.StreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccuRevAnnotationParser.class);

    private static final Pattern ANNOTATION_PATTERN
            = Pattern.compile("^\\s+(\\d+.\\d+)\\s+(\\w+)");   // version, user

    /**
     * Store annotation created by processStream.
     */
    private final Annotation annotation;

    /**
     * @param fileName the name of the file being annotated
     */
    public AccuRevAnnotationParser(String fileName) {
        annotation = new Annotation(fileName);
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
        try (BufferedReader reader
                = new BufferedReader(new InputStreamReader(input))) {
            String line;
            int lineno = 0;
            try {
                while ((line = reader.readLine()) != null) {
                    ++lineno;
                    Matcher matcher = ANNOTATION_PATTERN.matcher(line);

                    if (matcher.find()) {
                        // On Windows machines version shows up as
                        // <number>\<number>. To get search annotation
                        // to work properly, need to flip '\' to '/'.
                        // This is a noop on Unix boxes.
                        String version = matcher.group(1).replace('\\', '/');
                        String author  = matcher.group(2);
                        annotation.addLine(version, author, true);
                    } else {
                        LOGGER.log(Level.SEVERE,
                                "Did not find annotation in line {0}: [{1}]",
                                new Object[]{lineno, line});
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,
                        "Could not read annotations for " + annotation.getFilename(), e);
            }
        }
    }
}
