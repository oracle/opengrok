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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.index.IgnoredNames;

/**
 * Generates HTML listing of a Directory
 */
public class DirectoryListing {

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
     * Write a htmlized listing of the given directory to the given destination.
     *
     * @param dir the directory to list
     * @param out write destination
     * @param path virtual path of the directory (usually the path name of
     *  <var>dir</var> with the opengrok source directory stripped off).
     * @param files basenames of potential children of the directory to list.
     *  Gets filtered by {@link IgnoredNames}.
     * @return a possible empty list of README files included in the written
     *  listing.
     *
     * @throws java.io.IOException
     * @throws NullPointerException if a parameter except <var>files</var>
     *  is {@code null}
     */
    public List<String> listTo(File dir, Writer out, String path, List<String> files) throws IOException {
        // TODO this belongs to a jsp, not here
        ArrayList<String> readMes = new ArrayList<String>();
        int offset = -1;
        Format dateFormatter = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());
        if (path.length() != 0) {
            out.write("<p><a href=\"..\"><i>Up to higher level directory</i></a></p>");
        }
        EftarFileReader.FNode parentFNode = null;
        if (desc != null) {
            parentFNode = desc.getNode(path);
            if (parentFNode != null) {
                offset = parentFNode.childOffset;
            }
        }
        out.write("<table id=\"dirlist\">");
        out.write("<thead><tr><th/><th>Name</th><th>Date</th><th>Size</th>");
        if (offset > 0) {
            out.write("<th><tt>Description</tt></th>");
        }
        out.write("</tr></thead><tbody>");
        IgnoredNames ignoredNames = RuntimeEnvironment.getInstance().getIgnoredNames();

        if (files != null) {
            Collections.sort(files, String.CASE_INSENSITIVE_ORDER);
            for (String file : files) {
                if (ignoredNames.ignore(file)) {
                    continue;
                }
                File child = new File(dir, file);
                if (file.startsWith("README") || file.endsWith("README")
                    || file.startsWith("readme"))
                {
                    readMes.add(file);
                }
                boolean isDir = child.isDirectory();
                out.write("<tr><td><p class=\"");
                out.write(isDir ? 'r' : 'p');
                out.write("\"/></td><td><a href=\"");
                out.write(Util.URIEncodePath(file));
                if (isDir) {
                    out.write("/\"><b>");
                    out.write(file);
                    out.write("</b></a>/");
                } else {
                    out.write("\">");
                    out.write(file);
                    out.write("</a>");
                }
                Date lastm = new Date(child.lastModified());
                out.write("</td><td>");
                if (now - lastm.getTime() < 86400000) {
                    out.write("Today");
                } else {
                    out.write(dateFormatter.format(lastm));
                }
                out.write("</td><td>");
                // if (isDir) {
                    out.write(Util.readableSize(child.length()));
                // }
                out.write("</td>");
                if (offset > 0) {
                    String briefDesc = desc.getChildTag(parentFNode, file);
                    if (briefDesc == null) {
                        out.write("<td/>");
                    } else {
                        out.write("<td>");
                        out.write(briefDesc);
                        out.write("</td>");
                    }
                }
                out.write("</tr>");
            }
        }
        out.write("</tbody></table>");
        return readMes;
    }
}
