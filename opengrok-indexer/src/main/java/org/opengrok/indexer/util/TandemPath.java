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

/**
 * Represents a utility class for creating a path to operate in tandem with
 * an original path by adding a new file extension but limiting the length
 * of the filename component of the new path to 255 UTF-8 encoded bytes if
 * necessary by truncating and packing in a Base64-encoded SHA-256 hash of the
 * original file name component.
 */
public class TandemPath {

    /** Private to enforce static. */
    private TandemPath() {
    }

    /**
     * Appends an ASCII extension to the specified {@code filePath}, truncating
     * and packing in a SHA-256 hash if the UTF-8 encoding of the filename
     * component of the path would exceed 254 bytes and arriving at a final
     * size of 255 bytes in that special case.
     * @param filePath a defined instance
     * @param asciiExtension a defined instance that is expected to be only
     *                       ASCII so that its UTF-8 form is the same length
     * @return a transformed path whose filename component's UTF-8 encoding is
     * not more than 255 bytes.
     * @throws IllegalArgumentException {@code asciiExtension} is too long to
     * allow packing a SHA-256 hash in the transformation.
     */
    public static String join(String filePath, String asciiExtension) {

        File file = new File(filePath);
        String newName = TandemFilename.join(file.getName(), asciiExtension);
        File newFile = new File(file.getParent(), newName);
        return newFile.getPath();
    }
}
