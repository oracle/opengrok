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
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * Portions Copyright 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.web;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryException;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.index.IgnoredNames;

/**
 * Generates HTML listing of a Directory
 */
public class DirectoryListing {

    protected final static String DIRECTORY_SIZE_PLACEHOLDER = "-";
    private final EftarFileReader desc;
    private final long now;

    public DirectoryListing() {
        desc = null;
        now = System.currentTimeMillis();
    }

    public DirectoryListing(EftarFileReader desc) {
        this.desc = desc;
        now = System.currentTimeMillis();
    }

    /**
     * Write part of HTML code which contains file/directory last
     * modification time and size.
     *
     * @param out write destination
     * @param child the file or directory to use for writing the data
     * @param modTime the time of the last commit that touched {@code child},
     * or {@code null} if unknown
     * @param dateFormatter the formatter to use for pretty printing dates
     *
     * @throws NullPointerException if a parameter is {@code null}
     */
    private void printDateSize(Writer out, File child, Date modTime,
                               Format dateFormatter)
            throws IOException {
        long lastm = modTime == null ? child.lastModified() : modTime.getTime();

        out.write("<td>");
        if (now - lastm < 86400000) {
            out.write("Today");
        } else {
            out.write(dateFormatter.format(lastm));
        }
        out.write("</td><td>");
        if (child.isDirectory()) {
            out.write(DIRECTORY_SIZE_PLACEHOLDER);
        } else {
            out.write(Util.readableSize(child.length()));
        }
        out.write("</td>");
    }

    /**
     * Traverse directory until subdirectory with more than one item
     * (other than directory) or end of path is reached.
     * @param dir directory to traverse
     * @return string representing path with empty directories or the name of the directory
     */
    private static String getSimplifiedPath(File dir) {
        String[] files = dir.list();

        // Permissions can prevent getting list of items in the directory.
        if (files == null)
            return dir.getName();

        if (files.length == 1) {
            File entry = new File(dir, files[0]);
            IgnoredNames ignoredNames = RuntimeEnvironment.getInstance().getIgnoredNames();

            if (!ignoredNames.ignore(entry) && entry.isDirectory()) {
                return (dir.getName() + "/" + getSimplifiedPath(entry));
            }
        }

        return dir.getName();
    }

    /**
     * Write a HTML-ized listing of the given directory to the given destination.
     *
     * @param contextPath path used for link prefixes
     * @param dir the directory to list
     * @param out write destination
     * @param path virtual path of the directory (usually the path name of
     *  <var>dir</var> with the source root directory stripped off).
     * @param files basenames of potential children of the directory to list.
     *  Gets filtered by {@link IgnoredNames}.
     * @return a possible empty list of README files included in the written
     *  listing.
     * @throws org.opensolaris.opengrok.history.HistoryException when we cannot
     * get result from SCM
     *
     * @throws java.io.IOException when any I/O problem
     * @throws NullPointerException if a parameter except <var>files</var>
     *  is {@code null}
     */
    public List<String> listTo(String contextPath, File dir, Writer out,
            String path, List<String> files)
            throws HistoryException, IOException {
        // TODO this belongs to a jsp, not here
        ArrayList<String> readMes = new ArrayList<>();
        int offset = -1;
        EftarFileReader.FNode parentFNode = null;
        if (desc != null) {
            parentFNode = desc.getNode(path);
            if (parentFNode != null) {
                offset = parentFNode.childOffset;
            }
        }

        out.write("<table id=\"dirlist\" class=\"tablesorter tablesorter-default\">\n");
        out.write("<thead>\n");
        out.write("<tr>\n");
        out.write("<th class=\"sorter-false\"></th>\n");
        out.write("<th>Name</th>\n");
        out.write("<th class=\"sorter-false\"></th>\n");
        out.write("<th class=\"sort-dates\">Date</th>\n");
        out.write("<th class=\"sort-groksizes\">Size</th>\n");
        if (offset > 0) {
            out.write("<th><tt>Description</tt></th>\n");
        }
        out.write("</tr>\n</thead>\n<tbody>\n");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        IgnoredNames ignoredNames = env.getIgnoredNames();

        Format dateFormatter = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());

        // Print the '..' entry even for empty directories.
        if (path.length() != 0) {
            out.write("<tr><td><p class=\"'r'\"/></td><td>");
            out.write("<b><a href=\"..\">..</a></b></td><td></td>");
            printDateSize(out, dir.getParentFile(), null, dateFormatter);
            out.write("</tr>\n");
        }

        Map<String, Date> modTimes =
                HistoryGuru.getInstance().getLastModifiedTimes(dir);

        if (files != null) {
            for (String filename : files) {
                if (ignoredNames.ignore(env.getSourceRootPath() + path + filename)) {
                    continue;
                }
                File child = new File(dir, filename);
                if (ignoredNames.ignore(child)) {
                    continue;
                }
                if (filename.startsWith("README") || filename.endsWith("README")
                    || filename.startsWith("readme"))
                {
                    readMes.add(filename);
                }
                boolean isDir = child.isDirectory();
                out.write("<tr><td>");
                out.write("<p class=\"");
                out.write(isDir ? 'r' : 'p');
                out.write("\"/>");
                out.write("</td><td><a href=\"");
                if (isDir) {
                    String longpath = getSimplifiedPath(child);
                    out.write(Util.URIEncodePath(longpath));
                    out.write("/\"><b>");
                    int idx;
                    if ((idx = longpath.lastIndexOf('/')) > 0) {
                        out.write("<span class=\"simplified-path\">");
                        out.write(longpath.substring(0, idx + 1));
                        out.write("</span>");
                        out.write(longpath.substring(idx + 1));
                    } else {
                        out.write(longpath);
                    }
                    out.write("</b></a>/");
                } else {
                    out.write(Util.URIEncodePath(filename));
                    out.write("\">");
                    out.write(filename);
                    out.write("</a>");
                }
                out.write("</td>");
                Util.writeHAD(out, contextPath, path + filename, isDir);
                printDateSize(out, child, modTimes.get(filename), dateFormatter);
                if (offset > 0) {
                    String briefDesc = desc.getChildTag(parentFNode, filename);
                    if (briefDesc == null) {
                        out.write("<td/>");
                    } else {
                        out.write("<td>");
                        out.write(briefDesc);
                        out.write("</td>");
                    }
                }
                out.write("</tr>\n");
            }
        }
        out.write("</tbody>\n</table>");
        return readMes;
    }
}
