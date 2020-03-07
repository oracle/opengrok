/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * JFlex ZeroReader wrapper                                                *
 *                                                                         *
 * Copyright (C) 1998-2018  Gerwin Klein <lsf@jflex.de>                    *
 * All rights reserved.                                                    *
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.             *
 *                                                                         *
 * License: BSD                                                            *
 *                                                                         *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

package org.opengrok.indexer.analysis;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Represents a {@link Reader} wrapper that guards against implementations that
 * might return zero characters instead of properly blocking.
 *
 * <p>If zero character returns are rare, efficiency loss will be minimal.
 */
public class ZeroReader extends Reader {

    /** The underlying Reader that is being wrapped. */
    private final Reader reader;

    /**
     * Initializes to wrap a {@link Reader} that might not always block
     * appropriately in {@link #read(char[], int, int)}.
     */
    public ZeroReader(Reader reader) {
        this.reader = reader;
    }

    /**
     * Read {@code len} characters from the underlying {@link Reader} into the
     * buffer {@code cbuf} at offset {@code off}, blocking until input is
     * available or end-of-stream is reached.
     *
     * <p>Relies on the method {@link #read()} of the underlying {@link Reader}
     * to block until at least one character is available or end-of-stream is
     * reached.
     *
     * @param cbuf the buffer to write into
     * @param off the offset at which to write
     * @param len the maximum number of characters to write
     * @return -1 for end of stream; number of characters read otherwise.
     * Returns 0 if and only if len is equal to 0.
     * @exception IOException If an I/O error occurs
     * @exception IndexOutOfBoundsException
     *             If {@code off} is negative, or {@code len} is negative,
     *             or {@code len} is greater than {@code cbuf.length - off}
     */
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int n = reader.read(cbuf, off, len);
        if (n != 0 || len == 0) {
            return n;
        }

        if (len < 0) {
            throw new IndexOutOfBoundsException("len is negative");
        }
        if (off >= cbuf.length || off < 0) {
            throw new IndexOutOfBoundsException("off is out of cbuf bounds");
        }
        // Returns the character read, as an integer in the range 0 to 65535
        // (0x00-0xffff), or -1 if the end of the stream has been reached
        int c = reader.read();
        if (c == -1) {
            return -1;
        }
        cbuf[off] = (char) c;
        return 1;
    }

    /**
     * Closes the underlying {@link Reader}.
     *
     * @throws IOException if thrown by the underlying {@link Reader}
     */
    @Override
    public void close() throws IOException {
        reader.close();
    }

    /**
     * For testing only: gets the encoding of the underlying {@link Reader} if
     * it is an instanceof {@link InputStreamReader}.
     * @return a defined instance or {@code null}
     */
    String getUnderlyingEncoding() {
        if (reader instanceof InputStreamReader) {
            return ((InputStreamReader) reader).getEncoding();
        }
        return null;
    }
}
