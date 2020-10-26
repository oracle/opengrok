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
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The purpose of this class is to provide {@code StreamHandler} that limits the output
 * to specified number of lines. Compared to {@code SpoolHandler} it consumes
 * limited amount of heap.
 */
public class HeadHandler implements Executor.StreamHandler {
    private final int maxLines;

    private final List<String> lines = new ArrayList<>();
    private final Charset charset;

    private static final int bufferedReaderSize = 200;

    /**
     * Charset of the underlying reader is set to UTF-8.
     * @param maxLines maximum number of lines to store
     */
    public HeadHandler(int maxLines) {
        this.maxLines = maxLines;
        this.charset = StandardCharsets.UTF_8;
    }

    /**
     * @param maxLines maximum number of lines to store
     * @param charset character set
     */
    public HeadHandler(int maxLines, Charset charset) {
        this.maxLines = maxLines;
        this.charset = charset;
    }

    /**
     * @return number of lines read
     */
    public int count() {
        return lines.size();
    }

    /**
     * @param index index
     * @return line at given index. Will be non {@code null} for valid index.
     */
    public String get(int index) {
        return lines.get(index);
    }

    // for testing
    static int getBufferedReaderSize() {
        return bufferedReaderSize;
    }

    @Override
    public void processStream(InputStream input) throws IOException {
        try (BufferedInputStream bufStream = new BufferedInputStream(input);
        BufferedReader reader = new BufferedReader(new InputStreamReader(bufStream, this.charset),
                bufferedReaderSize)) {
            int lineNum = 0;
            while (lineNum < maxLines) {
                String line = reader.readLine();
                if (line == null) { // EOF
                    return;
                }
                lines.add(line);
                lineNum++;
            }

            // Read and forget the rest.
            byte[] buf = new byte[1024];
            while ((bufStream.read(buf)) != -1) {
                ;
            }
        }
    }
}
