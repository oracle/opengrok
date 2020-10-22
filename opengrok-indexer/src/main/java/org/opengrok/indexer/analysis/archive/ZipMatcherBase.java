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
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.archive;

import java.io.IOException;
import java.io.InputStream;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

/**
 * Represents an abstract base class for a ZIP archive
 * {@link FileAnalyzerFactory.Matcher} that can strictly check an "Extra field"
 * 16-bit ID code.
 * <p>
 * (Derived from /usr/src/cmd/file/file.c in OpenSolaris.)
 */
public abstract class ZipMatcherBase implements FileAnalyzerFactory.Matcher {

    private static final byte[] MAGIC = {'P', 'K', 3, 4};
    private static final int LOCHDRSIZ = 30;
    private static final int XFHSIZ = 4;

    @Override
    public boolean getIsPreciseMagic() {
        return true;
    }

    @Override
    public AnalyzerFactory isMagic(byte[] contents, InputStream in)
            throws IOException {
        assert in.markSupported();
        if (contents.length < MAGIC.length) {
            return null;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (contents[i] != MAGIC[i]) {
                return null;
            }
        }

        if (!doesCheckExtraFieldID()) {
            return forFactory();
        } else {
            byte[] buf = new byte[1024];
            in.mark(buf.length);
            int len = in.read(buf);
            in.reset();

            int xoff = LOCHDRSIZ + LOCNAM(buf);
            int xoff_end = Math.min(len, xoff + LOCEXT(buf));
            while ((xoff < xoff_end) && (len - xoff >= XFHSIZ)) {
                int xfhid = SH(buf, xoff);
                if (xfhid == strictExtraFieldID()) {
                    return forFactory();
                }
                int xfdatasiz = SH(buf, xoff + 2);
                xoff += XFHSIZ + xfdatasiz;
            }
            return null;
        }
    }

    /**
     * Derived classes must implement to get a value indicating if the ZIP
     * "Extra field ID" should be checked to match the value returne by
     * {@link #strictExtraFieldID()}.
     * @return {@code true} if "Extra field ID" should be checked
     */
    protected abstract boolean doesCheckExtraFieldID();

    /**
     * Derived classes that override {@link #doesCheckExtraFieldID()} to return
     * {@code true} must override this method to return the value that must
     * equal an "Extra field ID" in the ZIP file in order for
     * {@link #isMagic(byte[], java.io.InputStream)} to match and return a
     * defined instance.
     * @return {@code null}
     */
    protected Integer strictExtraFieldID() {
        return null;
    }

    private int CH(byte[] b, int n) {
        return b[n] & 0xff;
    }

    private int SH(byte[] b, int n) {
        return CH(b, n) | (CH(b, n + 1) << 8);
    }

    private int LOCNAM(byte[] b) {
        return SH(b, 26);
    }

    private int LOCEXT(byte[] b) {
        return SH(b, 28);
    }
}
