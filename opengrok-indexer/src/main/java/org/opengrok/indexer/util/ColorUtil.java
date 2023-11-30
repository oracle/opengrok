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
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
 */

package org.opengrok.indexer.util;

public class ColorUtil {

    private ColorUtil() {
    }

    /**
     * Return Color object from string. The following formats are allowed:
     * {@code A1B2C3},
     * {@code abc123}
     *
     * @param str hex string
     * @return Color object
     */
    public static Color fromHex(String str) {
        if (str.length() != 6) {
            throw new IllegalArgumentException("unsupported length:" + str);
        }

        return new Color(parseHexNumber(str, 0), parseHexNumber(str, 2), parseHexNumber(str, 4));
    }

    private static int parseHexNumber(String str, int pos) {
        return 16 * convertToDecimal(str, pos) + convertToDecimal(str, pos + 1);
    }

    private static int convertToDecimal(String str, int pos) {
        char ch = str.charAt(pos);
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }
        if (ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 10;
        }
        if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        }
        throw new IllegalArgumentException("unsupported char at " + pos + ":" + str);
    }
}
