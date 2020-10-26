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

import java.text.BreakIterator;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a subclass of {@link BreakIterator} that breaks at standard
 * OpenGrok EOL -- namely {@code \r\n}, {@code \n}, or {@code \r}.
 */
public class StrictLineBreakIterator extends BreakIterator {

    private final List<Integer> breaks = new ArrayList<>();
    private char peekChar = CharacterIterator.DONE;
    private CharacterIterator charIt;
    private int breakOffset = -1;

    public StrictLineBreakIterator() {
        charIt = new StringCharacterIterator("");
    }

    @Override
    public int first() {
        breaks.clear();
        breakOffset = -1;
        charIt.first();
        return 0;
    }

    @Override
    public int last() {
        int c;
        do {
            c = current();
        } while (next() != BreakIterator.DONE);
        return c;
    }

    @Override
    public int next(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n cannot be negative");
        }

        int noff = current();
        for (int i = 0; i < n; ++i) {
            noff = next();
            if (noff == BreakIterator.DONE) {
                return noff;
            }
        }
        return noff;
    }

    @Override
    public int next() {
        if (breakOffset + 1 < breaks.size()) {
            return breaks.get(++breakOffset);
        }

        char lastChar = CharacterIterator.DONE;
        int charOff;
        while (true) {
            char nextChar;
            if (peekChar != CharacterIterator.DONE) {
                nextChar = peekChar;
                peekChar = CharacterIterator.DONE;
            } else {
                nextChar = charIt.next();
            }

            switch (nextChar) {
                case CharacterIterator.DONE:
                    if (lastChar != CharacterIterator.DONE) {
                        charOff = charIt.getIndex();
                        breaks.add(charOff);
                        ++breakOffset;
                        return charOff;
                    } else {
                        return BreakIterator.DONE;
                    }
                case '\n':
                    // charOff is just past the LF
                    charOff = charIt.getIndex() + 1;
                    breaks.add(charOff);
                    ++breakOffset;
                    return charOff;
                case '\r':
                    charOff = charIt.getIndex() + 1;
                    peekChar = charIt.next();
                    switch (peekChar) {
                        case '\n':
                            peekChar = CharacterIterator.DONE;
                            // charOff is just past the LF
                            ++charOff;
                            breaks.add(charOff);
                            ++breakOffset;
                            return charOff;
                        case CharacterIterator.DONE:
                        default:
                            breaks.add(charOff);
                            ++breakOffset;
                            return charOff;
                    }
                default:
                    lastChar = nextChar;
                    break;
            }
        }
    }

    @Override
    public int previous() {
        if (breakOffset >= 0) {
            if (--breakOffset >= 0) {
                return breaks.get(breakOffset);
            }
            return 0;
        }
        return BreakIterator.DONE;
    }

    @Override
    public int following(int offset) {
        if (breaks.size() > 0 && breaks.get(breaks.size() - 1) > offset) {
            int lo = 0;
            int hi = breaks.size() - 1;
            int mid;
            while (lo <= hi) {
                mid = lo + (hi - lo) / 2;
                int boff = breaks.get(mid);
                if (offset < boff) {
                    if (mid < 1 || offset >= breaks.get(mid - 1)) {
                        return boff;
                    } else {
                        hi = mid - 1;
                    }
                } else {
                    lo = mid + 1;
                }
            }
            // This should not be reached.
            return BreakIterator.DONE;
        }

        int noff = BreakIterator.DONE;
        do {
            noff = next();
            if (noff > offset) {
                return noff;
            }
        } while (noff != BreakIterator.DONE);
        return noff;
    }

    @Override
    public int current() {
        if (breakOffset < 0) {
            return 0;
        }
        return breakOffset < breaks.size() ? breaks.get(breakOffset) :
            charIt.current();
    }

    @Override
    public CharacterIterator getText() {
        return (CharacterIterator) charIt.clone();
    }

    @Override
    public void setText(CharacterIterator newText) {
        if (newText == null) {
            throw new IllegalArgumentException("newText is null");
        }
        this.charIt = newText;
        this.breaks.clear();
        this.peekChar = newText.current();
        this.breakOffset = -1;
    }
}
