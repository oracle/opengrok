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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.history;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import java.util.Map.Entry;
import java.util.HashSet;
import org.opensolaris.opengrok.web.Util;

/**
 * Class representing file annotation, i.e., revision and author for the last
 * modification of each line in the file.
 */
public class Annotation {

    private final List<Line> lines = new ArrayList<Line>();
    private final HashMap<String, String> desc = new HashMap<String, String>();
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
    public HashSet<String> getRevisions() {
        HashSet<String> ret=new HashSet<String>();
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
    // to see if it will not be usefull, if title attribute of <a> loses it's breath
    public void writeTooltipMap(Writer out) throws IOException {
    	StringBuffer map = new StringBuffer();
    	map.append("<script type=\"text/javascript\">\n");
        map.append("    var desc = new Object();\n");
        for (Entry<String, String> entry : desc.entrySet()) {
        	map.append("desc['"+entry.getKey()+"'] = \""+entry.getValue()+"\";\n");
        }
        map.append("</script>\n");
    	out.write(map.toString());
    }

    @Override
    public String toString() {
    	StringBuffer sb = new StringBuffer();
    	for (Line line : lines) {
    		sb.append(line.revision+"|"+line.author+": "+"\n");
    	}
    	StringWriter sw = new StringWriter();
    	try {
			writeTooltipMap(sw);
		} catch (IOException e) {
			e.printStackTrace();
		}
		sb.append(sw.toString());

    	return sb.toString();
    } 
}
