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
package org.opengrok.indexer.search;

/**
 * Represents supplemental, per-file data stored after OpenGrok analysis.
 */
public class FileExtra {

    private final String filepath;
    private final Integer numlines;
    private final Integer loc;

    /**
     * Initializes an instance with specified file path, number of lines, and
     * lines-of-code.
     * @param filepath the file path
     * @param numlines the number of lines (null if unknown)
     * @param loc the lines-of-code (null if unknown)
     */
    public FileExtra(String filepath, Integer numlines, Integer loc) {
        this.filepath = filepath;
        this.numlines = numlines;
        this.loc = loc;
    }

    /**
     * @return the file path
     */
    public String getFilepath() {
        return filepath;
    }

    /**
     * @return the number of lines (null if unknown)
     */
    public Integer getNumlines() {
        return numlines;
    }

    /**
     * @return the lines-of-code (null if unknown)
     */
    public Integer getLoc() {
        return loc;
    }
}
