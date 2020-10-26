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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Represents a utility class for creating a filename to operate in tandem with
 * an original filename by adding a new file extension but limiting the length
 * of the new filename to 255 UTF-8 encoded bytes if necessary by truncating
 * and packing in a Base64-encoded SHA-256 hash of the original file name.
 */
public class TandemFilename {

    private static final int MAX_BYTES = 255;

    /**
     * One fewer than {@link #MAX_BYTES} as a cap for simple concatenation to
     * avoid the possibility of easily fabricating a collision against this
     * algorithm. I.e., a 255 byte tandem filename will always include a
     * computed hash and not just be the concatenation of original filename
     * plus new extension.
     */
    private static final int MAX_CAT_BYTES = MAX_BYTES - 1;

    /**
     * "Instances of Base64.Encoder class are safe for use by multiple
     * concurrent threads." --Oracle.
     */
    private static final Base64.Encoder encoder = Base64.getUrlEncoder();

    /** Private to enforce static. */
    private TandemFilename() {
    }

    /**
     * Appends an ASCII extension to the specified {@code filename}, truncating
     * and packing in a SHA-256 hash if the UTF-8 encoding would exceed 254
     * bytes and arriving at a final size of 255 bytes in that special case.
     * @param filename a defined instance
     * @param asciiExtension a defined instance that is expected to be only
     *                       ASCII so that its UTF-8 form is the same length
     * @return a transformed filename whose UTF-8 encoding is not more than 255
     * bytes.
     * @throws IllegalArgumentException thrown if {@code filename} has a
     * parent or if {@code asciiExtension} is too long to allow packing a
     * SHA-256 hash in the transformation.
     */
    public static String join(String filename, String asciiExtension) {

        File file = new File(filename);
        if (file.getParent() != null) {
            throw new IllegalArgumentException("filename can't have parent");
        }

        /*
         * If the original filename length * 4 (for longest possible UTF-8
         * encoding) plus asciiExtension length is not greater than one less
         * than 255, then quickly return the concatenation.
         */
        if (filename.length() * 4 + asciiExtension.length() <= MAX_CAT_BYTES) {
            return filename + asciiExtension;
        }
        return maybePackSha(filename, asciiExtension);
    }

    private static String maybePackSha(String filename, String asciiExtension) {

        byte[] uFilename = filename.getBytes(StandardCharsets.UTF_8);
        int nBytes = uFilename.length;
        if (nBytes + asciiExtension.length() <= MAX_CAT_BYTES) {
            // Here the UTF-8 encoding already allows for the new extension.
            return filename + asciiExtension;
        }

        /*
         * If filename has an ASCII extension already (of a reasonable length),
         * shift it to the new asciiExtension so that it won't be overwritten
         * by the packed hash.
         */
        int pos = filename.lastIndexOf('.');
        int extLength = filename.length() - pos;
        if (pos >= 0 && extLength < 30 && extLength > 1) {
            int i;
            for (i = pos + 1; i < filename.length(); ++i) {
                char ch = filename.charAt(i);
                if (!Character.isLetterOrDigit(ch) || ch > 'z') {
                    break;
                }
            }
            if (i >= filename.length()) {
                // By this point, we affirmed a letters/numbers extension.
                asciiExtension = filename.substring(pos) + asciiExtension;
                filename = filename.substring(0, pos);
                uFilename = filename.getBytes(StandardCharsets.UTF_8);
                nBytes = uFilename.length;
            }
        }

        // Pack the hash just before the file extension.
        asciiExtension = sha256base64(filename) + asciiExtension;

        /*
         * Now trim the filename by code points until the full UTF-8 encoding
         * fits within MAX_BYTES.
         */
        int newLength = filename.length();
        while (nBytes + asciiExtension.length() > MAX_BYTES) {
            int cp = filename.codePointBefore(newLength);
            int nChars = Character.charCount(cp);
            String c = filename.substring(newLength - nChars, newLength);
            nBytes -= c.getBytes(StandardCharsets.UTF_8).length;
            newLength -= nChars;

            if (newLength <= 0) {
                throw new IllegalArgumentException("asciiExtension too long");
            }
        }

        // Pad if necessary to exactly MAX_BYTES.
        if (nBytes + asciiExtension.length() != MAX_BYTES) {
            char[] pad = new char[MAX_BYTES - nBytes - asciiExtension.length()];
            Arrays.fill(pad, '_');
            asciiExtension = new String(pad) + asciiExtension;
        }

        return filename.substring(0, newLength) + asciiExtension;
    }

    private static String sha256base64(String value) {

        MessageDigest hasher;
        try {
            hasher = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            /*
             * This will not happen since "Every implementation of the Java
             * platform is required to support the following standard
             * MessageDigest algorithms: MD5, SHA-1, SHA-256."
             */
            throw new RuntimeException(e);
        }

        byte[] digest = hasher.digest(value.getBytes(StandardCharsets.UTF_8));
        return encoder.encodeToString(digest);
    }
}
