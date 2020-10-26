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
 * Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import java.util.regex.Pattern;

/**
 * Represents a container for utility methods concerned with URIs.
 */
public class UriUtils {

    /**
     * Represents the immutable return value of
     * {@link #trimUri(String, boolean, Pattern)}.
     */
    public static final class TrimUriResult {
        private final String uri;
        private final int pushBackCount;

        TrimUriResult(String uri, int pushBackCount) {
            this.uri = uri;
            this.pushBackCount = pushBackCount;
        }

        public String getUri() {
            return uri;
        }

        public int getPushBackCount() {
            return pushBackCount;
        }
    }

    /**
     * Trims a URI, specifying whether to enlist the
     * {@link StringUtils#countURIEndingPushback(String)} algorithm or the
     * {@link StringUtils#countPushback(String, Pattern)} or both.
     * <p>
     * If the pushback count is equal to the length of {@code url}, then the
     * pushback is set to zero -- in order to avoid a never-ending lexical loop.
     *
     * @param uri the URI string
     * @param shouldCheckEnding a value indicating whether to call
     * {@link StringUtils#countURIEndingPushback(String)}
     * @param collateralCapture optional pattern to call with
     * {@link StringUtils#countPushback(String, Pattern)}
     * @return a defined instance
     */
    public static TrimUriResult trimUri(String uri, boolean shouldCheckEnding,
            Pattern collateralCapture) {

        int n = 0;
        while (true) {
            /*
             * An ending-pushback could be present before a collateral capture,
             * so detect both in a loop (on a shrinking `url') until no more
             * shrinking should occur.
             */

            int subN = 0;
            if (shouldCheckEnding) {
                subN = StringUtils.countURIEndingPushback(uri);
            }
            int ccn = StringUtils.countPushback(uri, collateralCapture);
            if (ccn > subN) {
                subN = ccn;
            }

            // Increment if positive, but not if equal to the current length.
            if (subN > 0 && subN < uri.length()) {
                uri = uri.substring(0, uri.length() - subN);
                n += subN;
            } else {
                break;
            }
        }
        return new TrimUriResult(uri, n);
    }

    /** Private to enforce static. */
    private UriUtils() {
    }
}
