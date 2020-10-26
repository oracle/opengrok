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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.plain;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.ExpandTabsReader;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.util.StreamUtils;

/**
 * Represents a container for tests of {@link DefinitionsTokenStream}.
 */
public class DefinitionsTokenStreamTest {

    /**
     * Tests sampleplain.cc v. sampletags_cc with no expand-tabs and
     * no supplement when ctags's pattern excerpt is insufficient w.r.t.
     * `signature'.
     * @throws java.io.IOException I/O exception
     */
    @Test
    public void testCppDefinitionsForRawContentUnsupplemented()
            throws IOException {
        Map<Integer, SimpleEntry<String, String>> overrides = new TreeMap<>();
        overrides.put(44, new SimpleEntry<>(",", "parent_def"));
        overrides.put(45, new SimpleEntry<>(",", "element"));
        overrides.put(46, new SimpleEntry<>(",", "offset"));
        overrides.put(47, new SimpleEntry<>(",", "ant"));
        overrides.put(48, new SimpleEntry<>(",", "path"));

        testDefinitionsVsContent(false,
            "analysis/c/sample.cc",
            "analysis/c/sampletags_cc", 65, false,
            overrides);
    }

    /**
     * Tests sampleplain.cc v. sampletags_cc with no expand-tabs but
     * supplementing when ctags's pattern excerpt is insufficient w.r.t.
     * `signature'.
     * @throws java.io.IOException I/O exception
     */
    @Test
    public void testCppDefinitionsWithRawContent1() throws IOException {
        testDefinitionsVsContent(false,
            "analysis/c/sample.cc",
            "analysis/c/sampletags_cc", 65, true,
            null);
    }

    /**
     * Tests sampleplain.cc v. sampletags_cc with expand-tabs and
     * supplementing when ctags's pattern excerpt is insufficient w.r.t.
     * `signature'.
     * @throws java.io.IOException I/O exception
     */
    @Test
    public void testCppDefinitionsWithRawContent2() throws IOException {
        testDefinitionsVsContent(true,
            "analysis/c/sample.cc",
            "analysis/c/sampletags_cc", 65, true,
            null);
    }

    private void testDefinitionsVsContent(
            boolean expandTabs,
            String sourceResource,
            String tagsResource,
            int expectedCount,
            boolean doSupplement,
            Map<Integer, SimpleEntry<String, String>> overrides
    ) throws IOException {

        StreamSource src = getSourceFromResource(sourceResource);

        // Deserialize the ctags.
        int tabSize = expandTabs ? 8 : 0;
        String suppResource = doSupplement ? sourceResource : null;
        Definitions defs = StreamUtils.readTagsFromResource(tagsResource,
            suppResource, tabSize);

        // Read the whole input.
        StringBuilder bld = new StringBuilder();
        String source;
        try (Reader rdr = ExpandTabsReader.wrap(
                IOUtils.createBOMStrippedReader(src.getStream(),
                    StandardCharsets.UTF_8.name()), tabSize)) {
            int c;
            while ((c = rdr.read()) != -1) {
                bld.append((char) c);
            }
            source = bld.toString();
        }

        // Deserialize the token stream.
        DefinitionsTokenStream tokstream = new DefinitionsTokenStream();
        tokstream.initialize(defs, src, (in) -> {
            return ExpandTabsReader.wrap(in, tabSize);
        });

        // Iterate through stream.
        CharTermAttribute term = tokstream.getAttribute(
            CharTermAttribute.class);
        assertNotNull("CharTermAttribute", term);

        OffsetAttribute offs = tokstream.getAttribute(OffsetAttribute.class);
        assertNotNull("OffsetAttribute", offs);

        int count = 0;
        while (tokstream.incrementToken()) {
            ++count;
            String termValue = term.toString();

            String cutValue = source.substring(offs.startOffset(),
                offs.endOffset());

            // If an override exists, test it specially.
            if (overrides != null && overrides.containsKey(count)) {
                SimpleEntry<String, String> overkv = overrides.get(count);
                assertEquals("cut term override" + count, overkv.getKey(),
                    cutValue);
                assertEquals("cut term w.r.t. term override" + count,
                    overkv.getValue(), termValue);
                continue;
            }

            boolean cutContainsTerm = cutValue.endsWith(termValue);
            assertTrue("cut term" + count + " at " +
                (offs.startOffset()) + "-" + (offs.endOffset()) + "[" +
                cutValue + "] vs [" + termValue + "]", cutContainsTerm);
        }

        assertEquals("token count", expectedCount, count);
    }

    private static StreamSource getSourceFromResource(String name) {
        return new StreamSource() {
            @Override
            public InputStream getStream() throws IOException {
                InputStream srcres = getClass().getClassLoader().
                    getResourceAsStream(name);
                assertNotNull(name + " as resource,", srcres);
                return srcres;
            }
        };
    }
}
