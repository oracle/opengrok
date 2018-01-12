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
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.plain;

import java.util.Locale;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.analysis.TokenizerMode;
import org.opensolaris.opengrok.util.TextTrieMap;
%%
%public
%class PlainFullTokenizer
%extends JFlexSymbolMatcher
%unicode
%buffer 32766
%init{
    yyline = 1;
%init}
%int
%include CommonLexer.lexh
%caseless
%char
%{
    private static final Pattern POSSESSIVE = Pattern.compile("^\\w+'s$");

    private static final TextTrieMap<Object> CONTRACTIONS;

    private final int[] contractionLength = new int[1];

    private TokenizerMode mode = TokenizerMode.SYMBOLS_ONLY;

    static {
        CONTRACTIONS = new TextTrieMap<>();
        for (String word : new String[] {
            "ain't", "amn't", "aren't", "can't", "cain't", "could've",
            "couldn't", "daren't", "daresn't", "dasn't", "didn't",
            "doesn't", "don't", "e'er", "gonna", "gotta", "hadn't",
            "hasn't", "haven't", "he'd", "he'll", "he's", "how'd",
            "how'll", "how's", "i'd", "i'll", "i'm", "i'm'a", "i've",
            "isn't", "it'd", "it'll", "it's", "let's", "ma'am",
            "mayn't", "may've", "mightn't", "might've", "mustn't",
            "must've", "needn't", "ne'er", "o'clock", "o'er", "ol'",
            "oughtn't", "shan't", "she'd", "she'll", "she's",
            "should've", "shouldn't", "somebody's", "someone's",
            "something's", "that'll", "that're", "that's", "that'd",
            "there'd", "there're", "there's", "these're", "they'd",
            "they'll", "they're", "they've", "this's", "those're",
            "wasn't", "we'd", "we'd've", "we'll", "we're", "we've",
            "weren't", "what'd", "what'll", "what're", "what's",
            "what've", "when's", "where'd", "where're", "where's",
            "where've", "which's", "who'd", "who'd've", "who'll",
            "who're", "who's", "who've", "why'd", "why're", "why's",
            "won't", "would've", "wouldn't", "y'all", "you'd", "you'll",
            "you're", "you've" }) {
            CONTRACTIONS.put(word, word /* value is irrelevant */);
        }
    }

    /**
     * {@link PlainFullTokenizer} alters its behavior for modes which track all
     * non-whitespace so that its older parsing does not hurt newer support for
     * more comprehensive non-whitespace breaking nor support for plain-text
     * (English currently) contractions.
     * <p>
     * The older symbol tokenization splits contractions such as "there's" into
     * two tokens which can impact query-ability.
     */
    @Override
    public void setTokenizerMode(TokenizerMode value) {
        mode = value;
    }

    /**
     * Determines if {@code str} starts with a known contraction from a limited
     * collection of English words or is like a singular English possessive
     * ending in "'s".
     * @return 0 if {@code str} does not start with a contraction; or else the
     * length of the longest initial contraction
     */
    @Override
    public int getLongestContractionPrefix(String str) {
        String strlc = str.toLowerCase(Locale.ENGLISH);
        if (CONTRACTIONS.get(str, 0, contractionLength) != null) {
            return contractionLength[0];
        }
        return POSSESSIVE.matcher(strlc).matches() ? str.length() : 0;
    }
%}

//WhiteSpace     = [ \t\f\r]+|\n
Identifier = [a-zA-Z\p{Letter}_] [a-zA-Z\p{Letter}0-9\p{Number}_]*
Number = [0-9]+|[0-9]+\.[0-9]+| "0[xX]" [0-9a-fA-F]+
// No letters in the following, so no toLowerCase() needed in handling.
Printable = [\@\$\%\^\&\-+=\?\.\:]

%%
{Identifier}|{Number}|{Printable}    {
    String capturelc = yytext().toLowerCase(Locale.getDefault());
    switch (mode) {
        case SYMBOLS_AND_NON_WHITESPACE:
        case NON_WHITESPACE_ONLY:
            onNonSymbolMatched(capturelc, yychar);
            break;
        default:
            onSymbolMatched(capturelc, yychar);
            return yystate();
    }
}
[^]    {
    // below assumes locale from the shell/container, instead of just US
    switch (mode) {
        case SYMBOLS_AND_NON_WHITESPACE:
        case NON_WHITESPACE_ONLY:
            onNonSymbolMatched(yytext().toLowerCase(Locale.getDefault()),
                yychar);
            break;
        default:
            // noop
            break;
    }
}
