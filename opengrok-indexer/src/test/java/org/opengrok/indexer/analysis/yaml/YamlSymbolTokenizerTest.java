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
 * Copyright (c) 2023, Oracle and/or its affiliates.
 * Portions Copyright (c) 2023, Gino Augustine <gino.augustine@oracle.com>.
 */
package org.opengrok.indexer.analysis.yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.JFlexTokenizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Tests the {@link YamlSymbolTokenizer} class.
 * @author Gino Augustine
 */
public class YamlSymbolTokenizerTest {

    private final AbstractAnalyzer analyzer;

    public YamlSymbolTokenizerTest() {
        this.analyzer = YamlAnalyzerFactory.DEFAULT_INSTANCE.getAnalyzer();
    }

    private String[] getTermsFor(Reader r) {
        List<String> l = new LinkedList<>();
        JFlexTokenizer ts = (JFlexTokenizer) this.analyzer.tokenStream("refs", r);
        ts.setReader(r);
        CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
        try {
            ts.reset();
            while (ts.incrementToken()) {
                l.add(term.toString());
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return l.toArray(new String[0]);
    }

    @Test
    public void sampleTest() throws IOException {
        try (InputStream res = getClass().getClassLoader().getResourceAsStream("analysis/yaml/sample.yml");
             InputStreamReader r = new InputStreamReader(res, StandardCharsets.UTF_8)) {
            String[] termsFor = getTermsFor(r);
            assertArrayEquals(
                    new String[] {"apiarypath", "apiarypath"
                    },
                    termsFor);
        }
    }
}
