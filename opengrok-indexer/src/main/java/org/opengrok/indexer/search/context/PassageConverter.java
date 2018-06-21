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

package org.opengrok.indexer.search.context;

import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.lucene.search.uhighlight.Passage;
import org.opengrok.indexer.util.SourceSplitter;

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

            int m = splitter.findLineOffset(start);
            if (m < 0) {
                continue;
            }
            int n = splitter.findLineOffset(end - 1);
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
                int mm = splitter.findLineOffset(mstart);
                int mend = passage.getMatchEnds()[i];
                int nn = splitter.findLineOffset(mend - 1);
                if (mstart < mend && mm >= m && mm <= n && nn >= m && nn <= n) {
                    if (mm == nn) {
                        int lbeg = splitter.getPosition(mm);
                        int lstart = mstart - lbeg;
                        int lend = mend - lbeg;
                        LineHighlight lhigh = res.get(mm);
                        lhigh.addMarkup(PhraseHighlight.create(lstart, lend));
                    } else {
                        int lbeg = splitter.getPosition(mm);
                        int loff = mstart - lbeg;
                        LineHighlight lhigh = res.get(mm);
                        lhigh.addMarkup(PhraseHighlight.createStarter(loff));

                        lbeg = splitter.getPosition(nn);
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
            elideLine(lhi, line);
        }

        return res;
    }

    private void elideLine(LineHighlight lhi, String line) {
        int excess = line.length() - args.getContextWidth();
        if (excess <= 0) {
            return;
        }

        // Account for an ellipsis.
        int nellip = 1;
        ++excess;

        /*
         * The search/ view does not show leading whitespace anyway, so elide it
         * straight away.
         */
        int nwhsp0 = countStartingWhitespace(line);
        if (nwhsp0 > 0) {
            lhi.setLelide(Math.min(nwhsp0, excess));
            excess -= lhi.getLelide();
            if (excess <= 0) {
                return;
            }

            // Account for another ellipsis.
            ++nellip;
            ++excess;
        }

        int nwhspz = countEndingWhitespace(line);
        // If the end of the line can be elided, just truncate it.
        if (lhi.countMarkups() < 1 ||
                lhi.getMarkup(lhi.countMarkups() - 1).getLineEnd() <
                args.getContextWidth() || nwhspz >= excess) {
            lhi.setRelide(line.length() - excess);
            return;
        }

        /*
         * Find the width of bounds of markups.
         */
        int lbound = -1, rbound = -1;
        for (int i = 0; i < lhi.countMarkups(); ++i) {
            PhraseHighlight phi = lhi.getMarkup(i);
            if (phi.getLineStart() >= 0) {
                lbound = phi.getLineStart();
                break;
            } else if (phi.getLineStart() < 0) {
                lbound = phi.getLineStart();
                break;
            } else if (phi.getLineEnd() != Integer.MAX_VALUE) {
                lbound = phi.getLineEnd() - 1;
                break;
            } else if (phi.getLineEnd() == Integer.MAX_VALUE) {
                lbound = line.length() - 1;
                break;
            }
        }
        for (int i = lhi.countMarkups() - 1; i >= 0; --i) {
            PhraseHighlight phi = lhi.getMarkup(i);
            if (phi.getLineEnd() != Integer.MAX_VALUE) {
                rbound = phi.getLineEnd();
                break;
            } else if (phi.getLineEnd() != Integer.MAX_VALUE) {
                rbound = line.length();
                break;
            } else if (phi.getLineStart() >= 0) {
                rbound = phi.getLineStart() + 1;
                break;
            } else if (phi.getLineStart() < 0) {
                rbound = 1;
                break;
            }
        }
        // If the markup bounds are separated from the left margin...
        if (lbound > 0 && rbound > 0) {
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
                // Possibly account for another ellipsis.
                if (lhi.getLelide() < 1) {
                    ++nellip;
                    ++excess;
                }
                excess -= calcLeft - lhi.getLelide();
                lhi.setLelide(calcLeft);
            }
            // Continue below.
        }

        // Truncate the line finally.
        lhi.setRelide(line.length() - excess);
        if (nellip > 1) {
            /**
             * Possibly shift the left elide leftward in case the rough
             * estimate above was too far rightward.
             */
            lhi.setLelide(lhi.getRelide() - args.getContextWidth() + nellip);
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
