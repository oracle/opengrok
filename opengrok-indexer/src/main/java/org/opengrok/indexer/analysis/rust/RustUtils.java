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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.rust;

/**
 * Represents a container for Rust-related utility methods.
 */
public class RustUtils {

    /**
     * private to enforce singleton
     */
    private  RustUtils() {
    }

    /**
     * Counts the number of hashes ('#') before a terminating quote ('"') in
     * {@code capture}.
     * @param capture the Rust raw- or raw-byte-string initiator (e.g.,
     * {@code "r##\""})
     * @return the number of hashes counted
     */
    public static int countRawHashes(String capture) {
        if (!capture.endsWith("\"")) {
            throw new IllegalArgumentException("`capture' does not end in \"");
        }

        int n = 0;
        for (int i = capture.length() - 2; i >= 0; --i) {
            if (capture.charAt(i) != '#') {
                break;
            }
            ++n;
        }
        return n;
    }

    /**
     * Determines if the specified {@code capture} starts with a quote ('"')
     * and is followed by the specified number of hashes (plus possibly an
     * excess number of hashes), indicating the end of a raw- or raw-byte-
     * string.
     * @param capture the possible Rust raw- or raw-byte-string ender (e.g.,
     * {@code "\"####"})
     * @param rawHashCount the number of required hashes in order to be
     * considered "raw-ending"
     * @return true if the {@code capture} is determined to be "raw-ending" or
     * false otherwise (N.b., there may have been too many hashes captured, so
     * any excess of {@code yylength()} minus one minus {@code rawHashCount}
     * should be pushed back.
     */
    public static boolean isRawEnding(String capture, int rawHashCount) {
        if (!capture.startsWith("\"")) {
            throw new IllegalArgumentException(
                "`capture' does not start with \"");
        }

        int n = 0;
        for (int i = 1; i < capture.length(); ++i) {
            if (capture.charAt(i) != '#') {
                break;
            }
            ++n;
            if (n >= rawHashCount) {
                break;
            }
        }
        return n >= rawHashCount;
    }
}
