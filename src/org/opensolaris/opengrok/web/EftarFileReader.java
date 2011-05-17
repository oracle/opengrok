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
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.util.IOUtils;


/**
 * An Extremely Fast Tagged Attribute Read-only File Reader
 * Created on October 12, 2005
 *
 * @author Chandan
 */
public class EftarFileReader {

    private final RandomAccessFile f;
    private boolean isOpen;
    private static final Logger log = Logger.getLogger(EftarFileReader.class.getName());

    class FNode {

        public long offset;
        public long hash;
        public int childOffset;
        public int numChildren;
        public int tagOffset;

        public FNode() throws IOException {
            offset = f.getFilePointer();
            //System.err.println("offset " + offset);
            try {
                hash = f.readLong();
                childOffset = f.readUnsignedShort();
                numChildren = f.readUnsignedShort();
                tagOffset = f.readUnsignedShort();
            } catch (EOFException e) {
                numChildren = 0;
                tagOffset = 0;
            }
            //System.err.println("offset " + offset + " num children " + numChildren + " tagOff " + tagOffset);
        }

        public FNode(long hash, long offset, int childOffset, int num, int tagOffset) {
            this.hash = hash;
            this.offset = offset;
            this.childOffset = childOffset;
            this.numChildren = num;
            this.tagOffset = tagOffset;
        }

        public FNode get(long hash) throws IOException {
            if (childOffset == 0 || numChildren == 0) {
                return null;
            }
            return binarySearch(offset + childOffset, numChildren, hash);
        }

        private FNode binarySearch(long start, int len, long hash) throws IOException {
            //System.err.printf(" Searching [%x] in %5d elem from %x\n", hash, len, start);
            int b = 0;
            int e = len;
            while (b <= e) {
                int m = (b + e) / 2;
                f.seek(start + m * EftarFile.RECORD_LENGTH);
                long mhash = f.readLong();
                if (hash > mhash) {
                    b = m + 1;
                } else if (hash < mhash) {
                    e = m - 1;
                } else {
                    return new FNode(mhash, f.getFilePointer() - 8l, f.readUnsignedShort(), f.readUnsignedShort(), f.readUnsignedShort());
                }
            }
            return null;
        }

        public String getTag() throws IOException {
            if (tagOffset == 0) {
                return null;
            }
            f.seek(offset + tagOffset);
            byte[] tagString;
            if (childOffset == 0) {
                tagString = new byte[numChildren];
            } else {
                tagString = new byte[childOffset - tagOffset];
            }
            int len = f.read(tagString);
            if (len == -1) {
                throw new EOFException();
            }
            return new String(tagString, 0, len);
        }

        @Override
        public String toString() {
            String tagString = null;
            try {
                tagString = getTag();
            } catch (EOFException e) { // NOPMD
                // ignore
            } catch (IOException e) {
                log.log(Level.WARNING, "Got excption while getting the tag: ", e);
            }
            return "H[" + hash + "] num = " + numChildren + " tag = " + tagString;
        }
    }

    public EftarFileReader(String file) throws FileNotFoundException {
        f = new RandomAccessFile(file, "r");
        isOpen = true;
    }

    public FNode getNode(String path) throws IOException {
        StringTokenizer toks = new StringTokenizer(path, "/");
        f.seek(0);
        FNode n = new FNode();
        if (File.separator.equals(path) || path.length() == 0) {
            return n;
        }
        FNode next = null;
        while (toks.hasMoreTokens() && ((next = n.get(EftarFile.myHash(toks.nextToken()))) != null)) {
            n = next;
        }
        if (!toks.hasMoreElements()) {
            return next;
        }
        return null;
    }

    public String getChildTag(FNode fn, String name) throws IOException {
        if (fn != null && fn.childOffset != 0 && fn.numChildren != 0) {
            FNode ch = fn.binarySearch(fn.offset + fn.childOffset, fn.numChildren, EftarFile.myHash(name));
            if (ch != null) {
                return ch.getTag();
            }
        }
        return null;
    }

    public String get(String path) throws IOException {
        StringTokenizer toks = new StringTokenizer(path, "/");
        f.seek(0);
        FNode n = new FNode();
        FNode next;
        long tagOffset = 0;
        int tagLength = 0;
        while (toks.hasMoreTokens()) {
            String tok = toks.nextToken();
            if (tok == null || tok.length() == 0) {
                continue;
            }
            next = n.get(EftarFile.myHash(tok));
            if (next == null) {
                break;
            }
            if (next.tagOffset != 0) {
                tagOffset = next.offset + next.tagOffset;
                if (next.childOffset == 0) {
                    tagLength = next.numChildren;
                } else {
                    tagLength = next.childOffset - next.tagOffset;
                }
            }
            n = next;
        }
        if (tagOffset != 0) {
            f.seek(tagOffset);
            byte[] desc = new byte[tagLength];
            int len = f.read(desc);
            if (len == -1) {
                throw new EOFException();
            }
            return new String(desc, 0, len);
        }
        return "";
    }

    /**
     * Check, whether this instance has been already closed.
     * @return {@code true} if closed.
     */
    public boolean isClosed() {
        return !isOpen;
    }

    public void close() {
        if (isOpen) {
            IOUtils.close(f);
            isOpen = false;
        }
    }
}
