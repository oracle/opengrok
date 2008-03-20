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

package org.opensolaris.opengrok.analysis.archive;

import java.io.IOException;
import java.io.InputStream;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.executables.JarAnalyzerFactory;

public class ZipAnalyzerFactory extends FileAnalyzerFactory {
    private static final String[] SUFFIXES = {
        "ZIP"
    };

    private static final byte[] MAGIC = { 'P', 'K', 3, 4 };

    // Derived from /usr/src/cmd/file/file.c in OpenSolaris
    private static final Matcher MATCHER = new Matcher() {

        private static final int LOCHDRSIZ = 30;

        private int CH(byte[] b, int n) {
            return b[n] & 0xff;
        }

        private int SH(byte[] b, int n) {
            return CH(b, n) | (CH(b, n+1) << 8);
        }

        private int LOCNAM(byte[] b) {
            return SH(b, 26);
        }

        private int LOCEXT(byte[] b) {
            return SH(b, 28);
        }

        private static final int XFHSIZ = 4;

        public FileAnalyzerFactory isMagic(byte[] contents, InputStream in)
                throws IOException {
            assert in.markSupported();
            if (contents.length < MAGIC.length) return null;
            for (int i = 0; i < MAGIC.length; i++) {
                if (contents[i] != MAGIC[i]) return null;
            }

            byte[] buf = new byte[1024];
            in.mark(buf.length);
            int len = in.read(buf);
            in.reset();

            int xoff = LOCHDRSIZ + LOCNAM(buf);
            int xoff_end = Math.min(len, xoff + LOCEXT(buf));

            while ((xoff < xoff_end) && (len - xoff >= XFHSIZ)) {
                int xfhid = SH(buf, xoff);
                if (xfhid == 0xCAFE) {
                    return JarAnalyzerFactory.DEFAULT_INSTANCE;
                }
                int xfdatasiz = SH(buf, xoff + 2);
                xoff += XFHSIZ + xfdatasiz;
            }

            return ZipAnalyzerFactory.DEFAULT_INSTANCE;
        }

    };

    public static final ZipAnalyzerFactory DEFAULT_INSTANCE =
            new ZipAnalyzerFactory();

    private ZipAnalyzerFactory() {
        super(SUFFIXES, null, MATCHER, null, Genre.XREFABLE);
    }

    @Override
    protected FileAnalyzer newAnalyzer() {
        return new ZipAnalyzer(this);
    }
}
