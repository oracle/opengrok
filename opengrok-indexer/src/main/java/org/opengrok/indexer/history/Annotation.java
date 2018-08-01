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
 * Copyright (c) 2007, 2018 Oracle and/or its affiliates. All rights reserved.
 */

package org.opengrok.indexer.history;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import java.util.logging.Logger;

import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.web.Util;

/**
 * Class representing file annotation, i.e., revision and author for the last
 * modification of each line in the file.
 */
public class Annotation {

    private static final Logger LOGGER = LoggerFactory.getLogger(Annotation.class);

    private final List<Line> lines = new ArrayList<>();
    private final Map<String, String> desc = new HashMap<>();
    private final Map<String, Integer> fileVersions = new HashMap<>(); // maps revision to file version
    private int widestRevision;
    private int widestAuthor;
    private final String filename;

    public Annotation(String filename) {
        this.filename = filename;
    }

    /**
     * Gets the revision for the last change to the specified line.
     *
     * @param line line number (counting from 1)
     * @return revision string, or an empty string if there is no information
     * about the specified line
     */
    public String getRevision(int line) {
        try {
            return lines.get(line-1).revision;
        } catch (IndexOutOfBoundsException e) {
            return "";
        }
    }

    /**
     * Gets all revisions that are in use, first is the lowest one (sorted using natural order)
     *
     * @return list of all revisions the file has
     */
    public Set<String> getRevisions() {
        Set<String> ret=new HashSet<String>();
        for (Iterator<Line> it = this.lines.iterator(); it.hasNext();) {
            Line ln = it.next();
            ret.add(ln.revision);
        }
        return ret;
    }

    /**
     * Gets the author who last modified the specified line.
     *
     * @param line line number (counting from 1)
     * @return author, or an empty string if there is no information about the
     * specified line
     */
    public String getAuthor(int line) {
        try {
            return lines.get(line-1).author;
        } catch (IndexOutOfBoundsException e) {
            return "";
        }
    }

    /**
     * Gets the enabled state for the last change to the specified line.
     *
     * @param line line number (counting from 1)
     * @return true if the xref for this revision is enabled, false otherwise
     */
    public boolean isEnabled(int line) {
        try {
            return lines.get(line-1).enabled;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    /**
     * Returns the size of the file (number of lines).
     *
     * @return number of lines
     */
    public int size() {
        return lines.size();
    }

    /**
     * Returns the widest revision string in the file (used for pretty
     * printing).
     *
     * @return number of characters in the widest revision string
     */
    public int getWidestRevision() {
        return widestRevision;
    }

    /**
     * Returns the widest author name in the file (used for pretty printing).
     *
     * @return number of characters in the widest author string
     */
    public int getWidestAuthor() {
        return widestAuthor;
    }

    /**
     * Adds a line to the file.
     *
     * @param revision revision number
     * @param author author name
     */
    void addLine(String revision, String author, boolean enabled) {
        final Line line = new Line(revision, author, enabled);
        lines.add(line);
        widestRevision = Math.max(widestRevision, line.revision.length());
        widestAuthor = Math.max(widestAuthor, line.author.length());
    }

    void addDesc(String revision, String description) {
        desc.put(revision, Util.encode(description));
    }

    public String getDesc(String revision) {
        return desc.get(revision);
    }

    void addFileVersion(String revision, int fileVersion) {
        fileVersions.put(revision, fileVersion);
    }

    /**
     * Translates repository revision number into file version.
     * @param revision revision number
     * @return file version number. 0 if unknown. 1 first version of file, etc.
     */
    public int getFileVersion(String revision) {
        if( fileVersions.containsKey(revision) ) {
            return fileVersions.get(revision);
        } else {
            return 0;
        }
    }

    /**
     * @return Count of revisions on this file.
     */
    public int getFileVersionsCount() {
        return fileVersions.size();
    }

    /** Class representing one line in the file. */
    private static class Line {
        final String revision;
        final String author;
        final boolean enabled;
        Line(String rev, String aut, boolean ena) {
            revision = (rev == null) ? "" : rev;
            author = (aut == null) ? "" : aut;
            enabled = ena;
        }
    }

    public String getFilename() {
        return filename;
    }

    //TODO below might be useless, need to test with more SCMs and different commit messages
    // to see if it will not be useful, if title attribute of <a> loses it's breath
    public void writeTooltipMap(Writer out) throws IOException {
        out.append("<script type=\"text/javascript\">\nvar desc = new Object();\n");
        for (Entry<String, String> entry : desc.entrySet()) {
            out.append("desc['");
            out.append(entry.getKey());
            out.append("'] = \"");
            out.append(entry.getValue());
            out.append("\";\n");
        }
        out.append("</script>\n");
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        for (Line line : lines) {
            sw.append(line.revision);
            sw.append("|");
            sw.append(line.author);
            sw.append(": \n");
        }
        
        try {
            writeTooltipMap(sw);
        } catch (IOException e) {
            LOGGER.finest(e.getMessage());
        }

        return sw.toString();
    }
}
