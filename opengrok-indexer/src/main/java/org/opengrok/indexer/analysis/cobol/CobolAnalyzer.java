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
 * Copyright (c) 2026, Siyabend Urun <urunsiyabend@gmail.com>.
 */
package org.opengrok.indexer.analysis.cobol;

import org.apache.lucene.document.Document;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;
import org.opengrok.indexer.analysis.JFlexTokenizer;
import org.opengrok.indexer.analysis.JFlexXref;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.analysis.plain.AbstractSourceCodeAnalyzer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Locale;

/**
 * Analyzer for the COBOL language. Sniffs fixed- vs. free-format on each
 * {@link #analyze} call and dispatches to the matching tokenizer and xref.
 *
 * <p>{@code AnalyzerFactory.cachedAnalyzer} hands out a per-thread instance,
 * so the format flag can live on a plain instance field without extra
 * synchronization.
 */
@SuppressWarnings("java:S110")
public class CobolAnalyzer extends AbstractSourceCodeAnalyzer {

    /**
     * One-slot array so {@link #analyze} can update the flag after the
     * supplier lambda has already captured it. The lambda captures the
     * constructor parameter, not {@code this}, so this sidesteps Java's
     * "no {@code this} before {@code super(...)}" rule.
     */
    private final boolean[] fixedFormat;

    /** Creates a new instance. */
    protected CobolAnalyzer(FileAnalyzerFactory factory) {
        this(factory, new boolean[]{true});
    }

    private CobolAnalyzer(FileAnalyzerFactory factory, boolean[] holder) {
        super(factory, () -> new JFlexTokenizer(holder[0]
                ? new HyphenAwareCobolFixedSymbolTokenizer(AbstractAnalyzer.DUMMY_READER)
                : new HyphenAwareCobolFreeSymbolTokenizer(AbstractAnalyzer.DUMMY_READER)));
        this.fixedFormat = holder;
    }

    /**
     * Sniffs the format, then delegates. {@link StreamSource} returns a fresh
     * stream on every {@code getStream()} call, so reading twice is safe.
     */
    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut)
            throws IOException, InterruptedException {
        this.fixedFormat[0] = sniffFormat(src);
        super.analyze(doc, src, xrefOut);
    }

    /**
     * Returns {@code true} for fixed-format. Honors {@code >>SOURCE FREE} /
     * {@code >>SOURCE FIXED} directives. Otherwise scans up to 50 non-blank
     * lines for a free-only marker ({@code *>}, a letter at col 1, or a line
     * longer than 80 chars) and defaults to fixed if none is found.
     */
    boolean sniffFormat(StreamSource src) throws IOException {
        try (Reader reader = getReader(src.getStream());
             BufferedReader br = new BufferedReader(reader)) {

            int scanned = 0;
            String line;
            while (scanned < 50 && (line = br.readLine()) != null) {
                String upper = line.toUpperCase(Locale.ROOT);
                if (upper.contains(">>SOURCE FREE")) {
                    return false;
                }
                if (upper.contains(">>SOURCE FIXED")) {
                    return true;
                }
                if (line.isBlank()) {
                    continue;
                }
                scanned++;
                if (hasStrongFreeSignal(line)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** {@code *>} inline comment, letter at col 1, or line length above 80. */
    private static boolean hasStrongFreeSignal(String line) {
        if (line.isEmpty()) {
            return false;
        }
        if (Character.isLetter(line.charAt(0))) {
            return true;
        }
        if (line.contains("*>")) {
            return true;
        }
        return line.length() > 80;
    }

    /**
     * Test seam: pins the format flag so {@link #newXref} dispatches to the
     * matching xref without going through {@link #analyze}.
     */
    void setFixedFormat(boolean isFixed) {
        this.fixedFormat[0] = isFixed;
    }

    /**
     * {@code AnalyzerGuru} calls this once at registration, before any file
     * is sniffed, so a single value has to cover both formats. The
     * {@code CobolFree} ctags parser handles fixed-format input fine, while
     * the {@code Cobol} parser truncates symbols on free-format input.
     * @return {@code "CobolFree"}
     */
    @Override
    public String getCtagsLang() {
        return "CobolFree";
    }

    /**
     * Bump this when lexer behavior changes so stored documents get
     * re-analyzed.
     * @return 20260504_01
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20260504_01; // Edit comment above too!
    }

    /** Wraps the fixed- or free-format xref per the latest sniff. */
    @Override
    protected JFlexXref newXref(Reader reader) {
        return this.fixedFormat[0]
                ? new JFlexXref(new CobolFixedXref(reader))
                : new JFlexXref(new CobolFreeXref(reader));
    }
}
