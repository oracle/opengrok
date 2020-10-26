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
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.opengrok.indexer.util.Executor;

/**
 * handles parsing the output of the {@code cleartool annotate}
 * command into an annotation object.
 */
public class ClearCaseAnnotationParser implements Executor.StreamHandler {
    
    /**
     * Store annotation created by processStream.
     */
    private final Annotation annotation;
    
    /**
     * @param fileName the name of the file being annotated
     */
    public ClearCaseAnnotationParser(String fileName) {
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
        String line;
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(input))) {
            while ((line = in.readLine()) != null) {
                String[] parts = line.split("\\|");
                String aAuthor = parts[0];
                String aRevision = parts[1];
                aRevision = aRevision.replace('\\', '/');

                annotation.addLine(aRevision, aAuthor, true);
            }
        }
    }
}
