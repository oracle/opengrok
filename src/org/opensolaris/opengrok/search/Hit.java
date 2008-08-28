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
 * Copyright 2005 Trond Norbye.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.search;

import java.io.File;

/**
 * The hit class represents a single search hit
 *
 * @author Trond Norbye
 */
public class Hit implements Comparable<Hit> {
    /**
     * Holds value of property filename.
     */
    private String filename;
    
    /**
     * Holds value of property directory
     */
    private String directory;
    
    /**
     * Holds value of property line.
     */
    private String line;
    
    /**
     * Holds value of property lineno.
     */
    private String lineno;
    
    /**
     * Holds value of property binary.
     */
    private boolean binary;
    
    /**
     * Holds value of property alt used to hightlight alternating files.
     */
    private boolean alt;
    
    /**
     * path relative to source root.
     */
    private String path;
    
    /**
     * Creates a new instance of Hit
     */
    public Hit() {
        this(null, null, null, false, false);
    }
    
    /**
     * Creates a new instance of Hit
     *
     * @param filename The name of the file this hit represents
     * @param line The line containing the match
     * @param lineno The line number in the file the match was found
     * @param binary If this is a binary file or not
     */
    public Hit(String filename, String line, String lineno, boolean binary, boolean alt) {
        File file = new File(filename);
        this.path = filename;
        this.filename = file.getName();
        this.directory = file.getParent();
        if (directory == null) {
            directory = "";
        }
        this.line = line;
        this.lineno = lineno;
        this.binary = binary;
        this.alt = alt;
    }
    
    /**
     * Getter for property filename.
     *
     * @return Value of property filename.
     */
    public String getFilename() {
        return this.filename;
    }
    
    /**
     * Getter for property path.
     *
     * @return Value of property path.
     */
    public String getPath() {
        return this.path;
    }
    
    /**
     * Getter for property directory
     *
     * @return Value of property directory
     */
    public String getDirectory() {
        return this.directory;
    }
    
    /**
     * Setter for property filename.
     *
     * @param filename New value of property filename.
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }
    
    /**
     * Getter for property line.
     *
     * @return Value of property line.
     */
    public String getLine() {
        return this.line;
    }
    
    /**
     * Setter for property line.
     *
     * @param line New value of property line.
     */
    public void setLine(String line) {
        this.line = line;
    }
    
    /**
     * Getter for property lineno.
     *
     * @return Value of property lineno.
     */
    public String getLineno() {
        return this.lineno;
    }
    
    /**
     * Setter for property lineno.
     *
     * @param lineno New value of property lineno.
     */
    public void setLineno(String lineno) {
        this.lineno = lineno;
    }
    
    /**
     * Compare this object to another hit (in order to implement the comparable
     * interface)
     *
     * @param o The object to compare this object with
     *
     * @return the result of a toString().compareTo() of the filename
     */
    public int compareTo(Hit o) throws ClassCastException {
        return filename.compareTo(o.filename);
    }

    /**
     * Getter for property binary.
     *
     * @return Value of property binary.
     */
    public boolean isBinary() {
        return this.binary;
    }
    
    /**
     * Setter for property binary.
     *
     * @param binary New value of property binary.
     */
    public void setBinary(boolean binary) {
        this.binary = binary;
    }
    
    /**
     * Holds value of property tag.
     */
    private String tag;
    
    /**
     * Getter for property tag.
     * @return Value of property tag.
     */
    public String getTag() {
        
        return this.tag;
    }
    
    /**
     * Setter for property tag.
     * @param tag New value of property tag.
     */
    public void setTag(String tag) {
        
        this.tag = tag;
    }
    
    /**
     * Should this be alternate file?
     */
    public boolean getAlt() {
        return alt;
    }

    /**
     * Check if two objects are equal. Only consider the {@code filename} field
     * to match the return value of the {@link #compareTo(Hit)} method.
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof Hit) {
            return compareTo((Hit) o) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return filename.hashCode();
    }
}
