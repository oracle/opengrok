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
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
 * portions copyright 2005 Trond Norbye.  All rights reserved.
 * Use is subject to license terms.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search;

import java.io.File;

/**
 * The hit class represents a single search hit.
 *
 * @author Trond Norbye
 */
public class Hit {
    /**
     * Holds value of property filename.
     */
    private final String filename;

    /**
     * Holds value of property directory.
     */
    private final String directory;

    /**
     * Holds value of property line.
     */
    private String line;

    /**
     * Holds value of property line no.
     */
    private String lineno;

    /**
     * Holds value of property binary.
     */
    private boolean binary;

    /**
     * Holds value of property alt used to highlight alternating files.
     */
    private boolean alt;

    /**
     * path relative to source root.
     */
    private final String path;

    /**
     * Creates a new, possibly-defined instance.
     *
     * @param filename The name of the file this hit represents
     */
    public Hit(String filename) {
        this(filename, null, null, false, false);
    }

    /**
     * Creates a new, possibly-defined instance.
     *
     * @param filename The name of the file this hit represents
     * @param line The line containing the match
     * @param lineno The line number in the file the match was found
     * @param binary If this is a binary file or not
     * @param alt Is this the "alternate" file
     */
    public Hit(String filename, String line, String lineno, boolean binary, boolean alt) {
        if (filename != null) {
            File file = new File(filename);
            this.path = filename;
            this.filename = file.getName();
            final String parent = file.getParent();
            if (parent == null) {
                directory = "";
            } else {
                directory = parent;
            }
        } else {
            this.path = "";
            this.filename = "";
            this.directory = "";
        }
        this.line = line;
        this.lineno = lineno;
        this.binary = binary;
        this.alt = alt;
    }

    public String getFilename() {
        return this.filename;
    }

    public String getPath() {
        return this.path;
    }

    public String getDirectory() {
        return this.directory;
    }

    public String getLine() {
        return this.line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public String getLineno() {
        return this.lineno;
    }

    public void setLineno(String lineno) {
        this.lineno = lineno;
    }

    public boolean isBinary() {
        return this.binary;
    }

    public void setBinary(boolean binary) {
        this.binary = binary;
    }

    /**
     * Holds value of property tag.
     */
    private String tag;

    public String getTag() {

        return this.tag;
    }

    public void setTag(String tag) {

        this.tag = tag;
    }

    /**
     * Should this be alternate file?
     * @return true if this is the "alternate" file
     */
    public boolean getAlt() {
        return alt;
    }
}
