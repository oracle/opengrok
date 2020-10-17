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
 * Copyright (c) 2018-2019, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.search;

import org.apache.lucene.search.uhighlight.PassageFormatter;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.Definitions.Tag;
import org.opengrok.indexer.search.context.PassageConverter;
import org.opengrok.indexer.search.context.PhraseHighlight;
import org.opengrok.indexer.util.SourceSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Represents an abstract base class for highlighting formatters.
 */
public abstract class SearchFormatterBase extends PassageFormatter {

    /**
     * Matches a non-word character.
     */
    private static final Pattern NONWORD_CHAR = Pattern.compile("(?U)\\W");

    protected final PassageConverter cvt;
    protected final List<String> marks = new ArrayList<>();
    protected Definitions defs;

    /**
     * Cached splitter, keyed by {@link #originalText}.
     */
    protected SourceSplitter splitter;
    private String originalText;

    /**
     * Gets the optional definitions.
     * @return the defs
     */
    public Definitions getDefs() {
        return defs;
    }

    /**
     * Sets the optional definitions.
     * @param value definitions
     */
    public void setDefs(Definitions value) {
        this.defs = value;
    }

    protected SearchFormatterBase(PassageConverter converter) {
        cvt = converter;
    }

    /**
     * If the highlight is a sub-string wholly within the line, add it to the
     * {@link #marks} list.
     */
    protected void checkIfMark(String line, PhraseHighlight phi) {
        if (phi.getLineStart() >= 0 && phi.getLineEnd() <= line.length()) {
            marks.add(line.substring(phi.getLineStart(), phi.getLineEnd()));
        }
    }

    protected void updateOriginalText(String originalText) {
        if (this.originalText == null || !this.originalText.equals(originalText)) {
            splitter = new SourceSplitter();
            splitter.reset(originalText);
            this.originalText = originalText;
        }
    }

    /**
     * Search the cross product of {@code linetags} and {@code marks} for any
     * mark that starts with a {@link Tag#symbol} and where any subsequent
     * character is a non-word ({@code (?U)\W}) character.
     * @return a defined instance or {@code null}
     */
    protected Tag findTagForMark(List<Tag> linetags, List<String> marks) {
        for (Tag tag : linetags) {
            if (tag.type != null) {
                for (String mark : marks) {
                    if (mark.startsWith(tag.symbol) && (mark.length() == tag.symbol.length() ||
                            isNonWord(mark.charAt(tag.symbol.length())))) {
                        return tag;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isNonWord(char c) {
        String cword = String.valueOf(c);
        return NONWORD_CHAR.matcher(cword).matches();
    }
}
