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
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opengrok.indexer.util.Executor;

/**
 * Handles handles parsing the output of the {@code mnt annotate} command
 * into an annotation object. 
 */
public class MonotoneAnnotationParser implements Executor.StreamHandler {
    /**
     * Store annotation created by processStream.
     */
    private final Annotation annotation;

    /**
     * Pattern used to extract author/revision from the {@code mnt annotate} command.
     */
    private static final Pattern ANNOTATION_PATTERN
            = Pattern.compile("^(\\w+)\\p{Punct}\\p{Punct} by (\\S+)");
    
    /**
     * @param file the file being annotated
     */
    public MonotoneAnnotationParser(File file) {
        annotation = new Annotation(file.getName());
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
            String author = null;
            String rev = null;
            while ((line = in.readLine()) != null) {
                Matcher matcher = ANNOTATION_PATTERN.matcher(line);
                if (matcher.find()) {
                    rev = matcher.group(1);
                    author = matcher.group(2);
                    annotation.addLine(rev, author, true);
                } else {
                    annotation.addLine(rev, author, true);
                }
            }
        }
    }
}
