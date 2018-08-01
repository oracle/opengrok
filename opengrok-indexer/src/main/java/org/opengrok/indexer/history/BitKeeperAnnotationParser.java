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

package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;

/**
 * handles parsing the output of the {@code bk annotate} command
 * into an annotation object.
 *
 * @author James Service  {@literal <jas2701@googlemail.com>}
 */
public class BitKeeperAnnotationParser implements Executor.StreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BitKeeperAnnotationParser.class);

    /**
     * Store annotation created by processStream.
     */
    private final Annotation annotation;

    /**
     * @param fileName the name of the file being annotated
     */
    public BitKeeperAnnotationParser(String fileName) {
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

    /**
     * Process the output of a {@code bk annotate} command.
     *
     * Each input line should be in the following format:
     *   USER\tREVISION\tTEXT
     *
     * @param input the executor input stream
     * @throws IOException if the stream reader throws an IOException
     */
    @Override
    public void processStream(InputStream input) throws IOException {
        final BufferedReader in = new BufferedReader(new InputStreamReader(input));
        for (String line = in.readLine(); line != null; line = in.readLine()) {
            final String fields[] = line.split("\t");
            if (fields.length >= 2) {
                final String author = fields[0];
                final String rev = fields[1];
                annotation.addLine(rev, author, true);
            } else {
                LOGGER.log(Level.SEVERE, "Error: malformed BitKeeper annotate output {0}", line);
            }
        }
    }
}
