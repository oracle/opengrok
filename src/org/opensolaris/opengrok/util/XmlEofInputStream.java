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
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Enrich the base class with fake EOF signalization. The "0x0" is used as the
 * EOF character and therefore this is designed with use of XML transmission
 * where this character is not valid (it will not occur).
 *
 * @author Krystof Tulinger
 */
public class XmlEofInputStream extends FilterInputStream {

    public static final byte EOF = 0x0;
    private boolean isEof = false;

    public XmlEofInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (isEof) {
            return -1;
        }
        int r = super.read(b, off, len);
        isEof = b[off + r - 1] == EOF;
        return isEof ? r - 1 : r;
    }

    @Override
    public int read() throws IOException {
        if (isEof) {
            return -1;
        }
        int r = super.read();
        isEof = r == EOF;
        return isEof ? -1 : r;
    }

    @Override
    public void close() throws IOException {
        isEof = true;
    }

}
