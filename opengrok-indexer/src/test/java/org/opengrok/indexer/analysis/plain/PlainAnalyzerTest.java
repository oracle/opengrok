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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2021, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.plain;

import org.apache.lucene.document.Document;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.analysis.WriteXrefArgs;
import org.opengrok.indexer.analysis.Xrefer;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlainAnalyzerTest {

    private static StreamSource getStreamSource(final byte[] bytes) {
        return new StreamSource() {
            @Override
            public InputStream getStream() throws IOException {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    @Test
    void testXrefTimeout() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        long timeoutOriginal = env.getXrefTimeout();
        int timeout = 1;
        env.setXrefTimeout(timeout);
        assertEquals(timeout, env.getXrefTimeout());

        TestablePlainAnalyzer analyzer = new TestablePlainAnalyzer();
        Exception exception = assertThrows(InterruptedException.class, () -> analyzer.analyze(new Document(),
                getStreamSource("hi".getBytes()), new OutputStreamWriter(new ByteArrayOutputStream())));
        assertTrue(analyzer.writeXrefCalled);
        assertTrue(exception.getMessage().contains("failed to generate xref"));

        env.setXrefTimeout(timeoutOriginal);
    }

    private static class TestablePlainAnalyzer extends PlainAnalyzer {
        boolean writeXrefCalled;

        TestablePlainAnalyzer() {
            super(PlainAnalyzerFactory.DEFAULT_INSTANCE);
        }

        @Override
        public Xrefer writeXref(WriteXrefArgs args) throws IOException {
            writeXrefCalled = true;
            try {
                Thread.sleep(RuntimeEnvironment.getInstance().getXrefTimeout() * 2 * 1000);
            } catch (InterruptedException e) {
                throw new RuntimeException("failed to sleep");
            }
            return newXref(args.getIn());
        }
    }
}
