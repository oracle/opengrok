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
package org.opensolaris.opengrok.web;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;
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

    public void listTo(File dir, Writer out) throws IOException {
        String[] files = dir.list();
        if (files != null) {
            listTo(dir, out, dir.getPath(), files);
        }
    }

    /**
     * Write a listing of "dir" to "out"
     *
     * @param dir to be Listed
     * @param out writer to write
     * @param path Virtual Path of the directory
     * @param files childred of dir
     * @return a list of READMEs
     * @throws java.io.IOException
     *
     */
    public List listTo(File dir, Writer out, String path, String[] files) throws IOException {
        Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
        boolean alt = true;
        Format dateFormatter = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());
        out.write("<table cellspacing=\"0\" border=\"0\" id=\"dirlist\">");
        EftarFileReader.FNode parentFNode = null;
        if (!"".equals(path)) {
            out.write("<tr><td colspan=\"4\"><a href=\"..\"><i>Up to higher level directory</i></a></td></tr>");
        }
        if (desc != null) {
            parentFNode = desc.getNode(path);
        }
        out.write("<tr class=\"thead\"><th><tt>Name</tt></th><th><tt>Date</tt></th><th><tt>Size</tt></th>");

        if (parentFNode != null && parentFNode.childOffset > 0) {
            out.write("<th><tt>Description</tt></th>");
        }
        out.write("</tr>");
        ArrayList<String> readMes = new ArrayList<String>();
        IgnoredNames ignoredNames = RuntimeEnvironment.getInstance().getIgnoredNames();

        for (String file : files) {
            if (!ignoredNames.ignore(file)) {
                File child = new File(dir, file);
                if (file.startsWith("README") || file.endsWith("README") || file.startsWith("readme")) {
                    readMes.add(file);
                }
                alt = !alt;
                out.write("<tr align=\"right\"");
                out.write(alt ? " class=\"alt\"" : "");

                boolean isDir = child.isDirectory();
                out.write("><td align=\"left\"><tt><a href=\"" + Util.URIEncodePath(file) + (isDir ? "/\" class=\"r\"" : "\" class=\"p\"") + ">");
                if (isDir) {
                    out.write("<b>" + file + "</b></a>/");
                } else {
                    out.write(file + "</a>");
                }
                Date lastm = new Date(child.lastModified());
                out.write("</tt></td><td>" + ((now - lastm.getTime()) < 86400000 ? "Today" : dateFormatter.format(lastm))		+ "</td>");
                out.write("<td><tt>" + (isDir ? "" : Util.redableSize(child.length())) + "</tt></td>");

                if (parentFNode != null && parentFNode.childOffset > 0) {
                    String briefDesc = desc.getChildTag(parentFNode, file);
                    if (briefDesc == null) {
                        out.write("<td></td>");
                    } else {
                        out.write("<td align=\"left\">");
                        out.write(briefDesc);
                        out.write("</td>");
                    }
                }
                out.write("</tr>");
            }
        }
        out.write("</table>");
        return readMes;
    }

    public static void main(String[] args) {
        try {
            DirectoryListing dl = new DirectoryListing();
            File tolist = new File(args[0]);
            File outFile = new File(args[1]);
            BufferedWriter out = new BufferedWriter(new FileWriter(outFile));
            dl.listTo(tolist, out);
            out.close();
        } catch (Exception e) {
            OpenGrokLogger.getLogger().log(Level.WARNING, "Usage DirListing <dir> <output.html>", e);
        }
    }
}
