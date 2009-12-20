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
 * Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.analysis;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import org.opensolaris.opengrok.configuration.Project;

/**
 * Wrapper around Reader to expand tabs to spaces in the input.
 */
public class ExpandTabsReader extends FilterReader {
    /** The size of tabs. */
    private final int tabSize;

    /**
     * The position on the current line. Used to decide how many spaces to
     * insert to fill up to the next tab stop.
     */
    private int pos;

    /**
     * Number of spaces to insert (as replacement for a tab) before reading
     * more from the underlying stream.
     */
    private int spacesToInsert;

    /**
     * Create a new ExpandTabsReader to expand tabs to spaces.
     *
     * @param in the original input source
     * @param tabSize the size of tabs
     */
    ExpandTabsReader(Reader in, int tabSize) {
        super(in);
        this.tabSize = tabSize;
    }

    /**
     * Wrap a reader in an ExpandTabsReader if the project has custom tab
     * size settings.
     *
     * @param in the reader to wrap
     * @param p the project
     * @return {@code in} if the project doesn't have custom tab settings;
     * otherwise, an {@code ExpandTabsReader} that wraps {@code in} and expands
     * tabs as defined by the project's settings
     */
    public static Reader wrap(Reader in, Project p) {
        if (p != null && p.hasTabSizeSetting()) {
            return new ExpandTabsReader(in, p.getTabSize());
        } else {
            return in;
        }
    }

    @Override
    public int read() throws IOException {

        if (spacesToInsert > 0) {
            pos++;
            spacesToInsert--;
            return ' ';
        }

        int c = super.read();

        if (c == '\t') {
            // Fill up with spaces up to the next tab stop
            int spaces = tabSize - (pos % tabSize);
            pos++;
            spacesToInsert = spaces - 1;
            return ' ';
        }

        if (c == '\n' || c == '\r') {
            // Reset position on new line
            pos = 0;
        } else {
            pos++;
        }

        return c;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        for (int i = 0; i < len; i++) {
            int c = read();
            if (c == -1) {
                return (i > 0 ? i : -1);
            }
            cbuf[off + i] = (char) c;
        }
        return len;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n < 0L) {
            throw new IllegalArgumentException("n is negative");
        }

        long skipped = 0;
        for (long l = 0; l < n; l++) {
            int c = read();
            if (c == -1) break;
            skipped++;
        }

        return skipped;
    }

    @Override
    public boolean markSupported() {
        // Support for mark/reset has not been implemented.
        return false;
    }
}
