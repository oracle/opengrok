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
 * Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search.context;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import org.apache.lucene.search.uhighlight.Passage;
import org.opengrok.indexer.util.SourceSplitter;
import org.opengrok.indexer.util.StringUtils;

/**
 * Represents an object that can translate {@link Passage} instances into
 * indexed {@link LineHighlight} instances, taking into account a configurable
 * number of leading and trailing lines of context for each match.
 */
public class PassageConverter {

    private final ContextArgs args;

    /**
     * Initializes a converter for the specified arguments.
     * @param args required instance
     */
    public PassageConverter(ContextArgs args) {
        if (args == null) {
            throw new IllegalArgumentException("args is null");
        }
        this.args = args;
    }

    /**
     * @return the initialized value
     */
    public ContextArgs getArgs() {
        return args;
    }

    /**
     * Converts the specified passages into a sorted map of
     * {@link LineHighlight} instances keyed by line offsets.
     * @param passages a defined instance
     * @param splitter a defined instance
     * @return a defined instance
     */
    public SortedMap<Integer, LineHighlight> convert(Passage[] passages,
        SourceSplitter splitter) {

        SortedMap<Integer, LineHighlight> res = new TreeMap<>();
        for (Passage passage : passages) {
            int start = passage.getStartOffset();
            int end = passage.getEndOffset();
            if (start >= end) {
                continue;
            }

            int m = splitter.findLineIndex(start);
            if (m < 0) {
                continue;
            }
            int n = splitter.findLineIndex(end - 1);
            if (n < 0) {
                continue;
            }

            m = Math.max(0, m - args.getContextSurround());
            n = Math.min(splitter.count() - 1, n + args.getContextSurround());

            // Ensure an entry in `res' for every passage line.
            for (int i = m; i <= n; ++i) {
                if (!res.containsKey(i)) {
                    res.put(i, new LineHighlight(i));
                }
            }

            // Create LineHighlight entries for passage matches.
            for (int i = 0; i < passage.getNumMatches(); ++i) {
                int mstart = passage.getMatchStarts()[i];
                int mm = splitter.findLineIndex(mstart);
                int mend = passage.getMatchEnds()[i];
                int nn = splitter.findLineIndex(mend - 1);
                if (mstart < mend && mm >= m && mm <= n && nn >= m && nn <= n) {
                    if (mm == nn) {
                        int lbeg = splitter.getOffset(mm);
                        int lstart = mstart - lbeg;
                        int lend = mend - lbeg;
                        LineHighlight lhigh = res.get(mm);
                        lhigh.addMarkup(PhraseHighlight.create(lstart, lend));
                    } else {
                        int lbeg = splitter.getOffset(mm);
                        int loff = mstart - lbeg;
                        LineHighlight lhigh = res.get(mm);
                        lhigh.addMarkup(PhraseHighlight.createStarter(loff));

                        lbeg = splitter.getOffset(nn);
                        loff = mend - lbeg;
                        lhigh = res.get(nn);
                        lhigh.addMarkup(PhraseHighlight.createEnder(loff));

                        /*
                         * Designate any intermediate lines as
                         * wholly-highlighted
                         */
                        for (int j = mm + 1; j <= nn - 1; ++j) {
                            lhigh = res.get(j);
                            lhigh.addMarkup(PhraseHighlight.createEntire());
                        }
                    }
                }
            }
        }

        /*
         * Condense PhraseHighlight instances within lines, and elide as
         * necessary to the reportable length.
         */
        for (LineHighlight lhi : res.values()) {
            lhi.condenseMarkups();
            String line = splitter.getLine(lhi.getLineno());
            Matcher eolMatcher = StringUtils.STANDARD_EOL.matcher(line);
            if (eolMatcher.find()) {
                line = line.substring(0, eolMatcher.start());
            }
            elideLine(lhi, line);
        }

        return res;
    }

    private void elideLine(LineHighlight lhi, String line) {
        int excess = line.length() - args.getContextWidth();
        if (excess <= 0) {
            return;
        }

        /*
         * The search/ view does not show leading whitespace anyway, so elide it
         * straight away.
         */
        int nwhsp0 = countStartingWhitespace(line);
        if (nwhsp0 > 0) {
            // Account for an ellipsis.
            ++excess;
            int leftAdj = Math.min(nwhsp0, excess);
            lhi.setLelide(leftAdj);
            excess -= leftAdj;
            if (excess <= 0) {
                return;
            }
        }

        int nwhspz = countEndingWhitespace(line);
        /*
         * If the end of the line has enough whitespace to be elided (pre-
         * accounting for another ellipsis), just truncate it.
         */
        if (lhi.countMarkups() < 1 ||
                lhi.getMarkup(lhi.countMarkups() - 1).getLineEnd() <
                        args.getContextWidth() ||
                nwhspz >= excess + 1) {
            // Account for an ellipsis.
            ++excess;
            lhi.setRelide(line.length() - excess);
            return;
        }

        /*
         * Find the width of bounds of markups.
         */
        int lbound = -1, rbound = -1;
        if (lhi.countMarkups() > 0) {
            PhraseHighlight phi = lhi.getMarkup(0);
            lbound = phi.getLineStart();
            if (lbound >= line.length()) {
                lbound = line.length() - 1;
            }

            phi = lhi.getMarkup(lhi.countMarkups() - 1);
            rbound = phi.getLineEnd();
            if (rbound > line.length()) {
                rbound = line.length();
            }
        }

        /*
         * If the markup bounds are separated from the left margin, calculate
         * elision bounds that contain as much of the highlighted area as
         * possible, favoring the leftward highlights if the highlighted area
         * exceeds the context-width.
         */
        if (lbound > 0 && rbound >= lbound) {
            /*
             * First use a rough estimate of three-quarters of a context-width
             * before the midpoint of lbound and rbound.
             */
            int calcLeft = Math.max(0, (lbound + rbound) / 2 -
                args.getContextWidth() * 3 / 4 - 1);
            // If past the lbound, then snap it left.
            if (calcLeft > lbound) {
                calcLeft = lbound;
            }
            if (calcLeft > lhi.getLelide()) {
                // Possibly account for an ellipsis.
                if (lhi.getLelide() < 1) {
                    ++excess;
                }
                int leftAdj = Math.min(calcLeft - lhi.getLelide(), excess);
                excess -= leftAdj;
                lhi.setLelide(lhi.getLelide() + leftAdj);
            }
        }

        // Possibly truncate the line finally.
        if (excess > 0) {
            // Account for another ellipsis.
            ++excess;
            lhi.setRelide(line.length() - excess);
            /*
             * Possibly shift the left elision leftward in case the rough
             * estimate above was too far rightward.
             */
            if (lhi.getLelide() > 0) {
                lhi.setLelide(lhi.getRelide() - args.getContextWidth() +
                        2 /* two ellipses */);
            }
        }
    }

    private int countStartingWhitespace(String line) {
        int n = 0;
        for (int i = 0; i < line.length(); ++i) {
            char c = line.charAt(i);
            if (!Character.isWhitespace(c)) {
                break;
            }
            ++n;
        }
        return n;
    }

    private int countEndingWhitespace(String line) {
        int n = 0;
        for (int i = line.length() - 1; i >= 0; --i) {
            char c = line.charAt(i);
            if (!Character.isWhitespace(c)) {
                break;
            }
            ++n;
        }
        return n;
    }
}
