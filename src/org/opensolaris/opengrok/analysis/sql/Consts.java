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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis.sql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

class Consts {
    private static final Set<String> reservedKeywords = new HashSet<String>();
    static {
        try {
            populateKeywordSet(reservedKeywords, "sql2003reserved.dat");
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static void populateKeywordSet(Set<String> set, String file)
            throws IOException
    {
        String line;
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(
                    Consts.class.getResourceAsStream(file), "US-ASCII"));
        try {
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase(Locale.US);
                if (!line.startsWith("#")) {
                    set.add(line);
                }
            }
        } finally {
            reader.close();
        }
    }

    static boolean isReservedKeyword(String word) {
        return reservedKeywords.contains(word.toLowerCase(Locale.US));
    }
}
