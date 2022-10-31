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
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * BazaarAnnotationParser handles parsing the output of the {@code bzr blame}
 * command into an annotation object.
 */
public class BazaarAnnotationParser implements Executor.StreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BazaarAnnotationParser.class);

    /**
     * Store annotation created by processStream.
     */
    private final Annotation annotation;

    /**
     * Pattern used to extract author/revision.
     */
    private static final Pattern BLAME_PATTERN = Pattern.compile("^\\W*(\\S+)\\W+(\\S+).*$");

    private final String fileName;

    /**
     * @param fileName the name of the file being annotated
     */
    public BazaarAnnotationParser(String fileName) {
        annotation = new Annotation(fileName);
        this.fileName = fileName;
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
            String line = "";
            int lineno = 0;
            Matcher matcher = BLAME_PATTERN.matcher(line);
            while ((line = in.readLine()) != null) {
                ++lineno;
                matcher.reset(line);
                if (matcher.find()) {
                    String rev = matcher.group(1);
                    String author = matcher.group(2).trim();
                    annotation.addLine(rev, author, true);
                } else {
                    LOGGER.log(Level.WARNING,
                            "Error: did not find annotation in line {0} for ''{1}'': [{2}]",
                            new Object[]{String.valueOf(lineno), this.fileName, line});
                }
            }
        }
    }
}
