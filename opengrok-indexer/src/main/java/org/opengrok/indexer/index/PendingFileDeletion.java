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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

/**
 * Represents the metadata for a pending file deletion.
 */
public final class PendingFileDeletion {
    private final String absolutePath;

    public PendingFileDeletion(String absolutePath) {
        this.absolutePath = absolutePath;
    }

    /**
     * @return the absolute path
     */
    public String getAbsolutePath() {
        return absolutePath;
    }

    /**
     * Compares {@code absolutePath} to the other object's value.
     * @param o other object for comparison
     * @return {@code true} if the two compared objects are identical;
     * otherwise false
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PendingFileDeletion)) {
            return false;
        }
        PendingFileDeletion other = (PendingFileDeletion) o;
        return this.absolutePath.equals(other.absolutePath);
    }

    @Override
    public int hashCode() {
        return this.absolutePath.hashCode();
    }
}
