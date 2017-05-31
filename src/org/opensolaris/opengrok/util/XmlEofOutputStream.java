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

import java.io.EOFException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author Krystof Tulinger
 */
public class XmlEofOutputStream extends FilterOutputStream {

    public static final byte EOF = XmlEofInputStream.EOF;
    private boolean isClosed = false;

    public XmlEofOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (isClosed) {
            throw new EOFException("Stream is already finished.");
        }
        super.write(b, off, len);
    }

    @Override
    public void write(int b) throws IOException {
        if (isClosed) {
            throw new EOFException("Stream is already finished.");
        }
        super.write(b);
    }

    @Override
    public void close() throws IOException {
        if (!isClosed) {
            write(XmlEofInputStream.EOF);
            super.flush();
        }
        isClosed = true;
    }

}
