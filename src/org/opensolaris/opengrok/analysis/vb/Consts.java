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
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis.vb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("PMD.AvoidThrowingRawExceptionTypes")
public final class Consts {

    private static final Set<String> reservedKeywords;

    static {
        HashSet<String> kwds = new HashSet<String>();
        try {

            populateKeywordSet(kwds, "vbReservedWords.dat");
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        reservedKeywords = Collections.unmodifiableSet(kwds);
    }

    private Consts() {
        // Util class, can not be constructed.
    }

    private static void populateKeywordSet(Set<String> set, String file)
            throws IOException {
        String line, lline;
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(
                Consts.class.getResourceAsStream(file), "US-ASCII"));
        try {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                lline = line.toLowerCase(Locale.US);
                if (line.charAt(0) != '#') {
                    set.add(line);
                    set.add(lline);
                }
            }
        } finally {
            reader.close();
        }
    }

    static Set<String> getReservedKeywords() {
        return reservedKeywords;
    }
}
