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
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Krystof Tulinger (tulinkry).
 */
package org.opengrok.indexer.util;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * This class implements portions of semantic versioning from version noted as
 * <pre>
 *     a.b.c.d.e.f
 * </pre>
 * where
 * <pre>
 *     a - major
 *     b - minor
 *     c - patch
 *     d ... - others
 * </pre>
 *
 * @author Krystof Tulinger (tulinkry)
 * @see <a href="https://semver.org/">https://semver.org/</a>
 */
public class Version implements Comparable<Version> {
    private final Integer[] versions;

    /**
     * Construct the version from integer parts.
     * The order is:
     * <ol>
     * <li>major</li>
     * <li>minor</li>
     * <li>patch</li>
     * <li>... others</li>
     * </ol>
     *
     * @param parts integer values for version partials
     */
    public Version(Integer... parts) {
        // cut off trailing zeros
        int cutOffLength = parts.length;
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i] != 0) {
                break;
            }
            cutOffLength--;
        }
        versions = Arrays.copyOf(parts, cutOffLength);
    }

    /**
     * Construct the version from a string.
     *
     * @param string string representing the version (e. g. 1.2.1)
     * @return the new instance of version from the string
     * @throws NumberFormatException when parts can not be converted to integers
     */
    public static Version from(String string) throws NumberFormatException {
        return new Version(
                Stream.of(string.trim().split("\\."))
                      .map(String::trim)
                      .map(Integer::parseInt)
                      .toArray(Integer[]::new)
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Version version = (Version) o;
        return Arrays.equals(versions, version.versions);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(versions);
    }

    @Override
    public int compareTo(Version o) {
        if (o == null) {
            return 1;
        }

        // first different number determines the result
        for (int i = 0; i < Math.min(versions.length, o.versions.length); i++) {
            if (!versions[i].equals(o.versions[i])) {
                return Integer.compare(versions[i], o.versions[i]);
            }
        }

        // the comparable parts are the same, the longer has some trailing non-null element which
        // makes it greater then the shorter
        // e. g. 1.0.0.0.1 vs. 1.0.0
        return Integer.compare(versions.length, o.versions.length);
    }
}
