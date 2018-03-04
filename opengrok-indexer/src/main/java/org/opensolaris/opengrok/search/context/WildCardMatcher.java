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
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
 * 
 * Portions Apache software license, see below
 * 
 */
package org.opensolaris.opengrok.search.context;

public class WildCardMatcher extends LineMatcher {

    final String pattern;

    public WildCardMatcher(String pattern, boolean caseInsensitive) {
        super(caseInsensitive);
        this.pattern = normalizeString(pattern);
    }

    @Override
    public int match(String token) {
        String tokenToMatch = normalizeString(token);
        return wildcardEquals(pattern, 0, tokenToMatch, 0)
                ? MATCHED
                : NOT_MATCHED;
    }
    //TODO below might be buggy, we might need to rewrite this anyways
    // so far keep it for the sake of 4.0 port
    /**
     * Licensed to the Apache Software Foundation (ASF) under one or more
     * contributor license agreements. See the NOTICE file distributed with this
     * work for additional information regarding copyright ownership. The ASF
     * licenses this file to You under the Apache License, Version 2.0 (the
     * "License"); you may not use this file except in compliance with the
     * License. You may obtain a copy of the License at
     *
     * http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
     * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
     * License for the specific language governing permissions and limitations
     * under the License.
     */
    /**
     * ******************************************
     * String equality with support for wildcards
     * ******************************************
     */
    public static final char WILDCARD_STRING = '*';
    public static final char WILDCARD_CHAR = '?';

    /**
     * Determines if a word matches a wildcard pattern. <small>Work released by
     * Granta Design Ltd after originally being done on company time.</small>
     */
    public static boolean wildcardEquals(String pattern, int patternIdx,
            String string, int stringIdx) {
        int p = patternIdx;

        for (int s = stringIdx;; ++p, ++s) {
            // End of string yet?
            boolean sEnd = (s >= string.length());
            // End of pattern yet?
            boolean pEnd = (p >= pattern.length());

            // If we're looking at the end of the string...
            if (sEnd) {
                // Assume the only thing left on the pattern is/are wildcards
                boolean justWildcardsLeft = true;

                // Current wildcard position
                int wildcardSearchPos = p;
                // While we haven't found the end of the pattern,
                // and haven't encountered any non-wildcard characters
                while (wildcardSearchPos < pattern.length() && justWildcardsLeft) {
                    // Check the character at the current position
                    char wildchar = pattern.charAt(wildcardSearchPos);

                    // If it's not a wildcard character, then there is more
                    // pattern information after this/these wildcards.
                    if (wildchar != WILDCARD_CHAR && wildchar != WILDCARD_STRING) {
                        justWildcardsLeft = false;
                    } else {
                        // to prevent "cat" matches "ca??"
                        if (wildchar == WILDCARD_CHAR) {
                            return false;
                        }

                        // Look at the next character
                        wildcardSearchPos++;
                    }
                }

                // This was a prefix wildcard search, and we've matched, so
                // return true.
                if (justWildcardsLeft) {
                    return true;
                }
            }

            // If we've gone past the end of the string, or the pattern,
            // return false.
            if (sEnd || pEnd) {
                break;
            }

            // Match a single character, so continue.
            if (pattern.charAt(p) == WILDCARD_CHAR) {
                continue;
            }

            //
            if (pattern.charAt(p) == WILDCARD_STRING) {
                // Look at the character beyond the '*' characters.
                while (p < pattern.length() && pattern.charAt(p) == WILDCARD_STRING) {
                    ++p;
                }
                // Examine the string, starting at the last character.
                for (int i = string.length(); i >= s; --i) {
                    if (wildcardEquals(pattern, p, string, i)) {
                        return true;
                    }
                }
                break;
            }
            if (pattern.charAt(p) != string.charAt(s)) {
                break;
            }
        }
        return false;
    }
}
