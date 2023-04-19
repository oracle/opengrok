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
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.analysis.NullableNumLinesLOC;
import org.opengrok.indexer.configuration.PathAccepter;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.CacheException;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.DirectoryEntry;
import org.opengrok.indexer.web.EftarFileReader;
import org.opengrok.indexer.web.Util;

/**
 * Generates HTML listing of a Directory.
 */
public class DirectoryListing {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryListing.class);

    protected static final String BLANK_PLACEHOLDER = "-";
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
     * Write part of HTML code which contains file/directory last modification time and size.
     * The size printed for directories will be always {@link #BLANK_PLACEHOLDER}.
     * The time printed will be string representation of the non {@code null} time or {@link #BLANK_PLACEHOLDER}.
     * @param out write destination
     * @param file the file or directory to use for writing the data
     * @param date the time of the last commit that touched {@code file} or {@code null} if unknown
     * @param dateFormatter the formatter to use for pretty printing dates
     *
     * @throws IOException when writing to the {@code out} parameter failed
     */
    public void printDateSize(Writer out, File file, Date date, Format dateFormatter) throws IOException {
        out.write("<td>");
        if (date == null) {
            out.write(BLANK_PLACEHOLDER);
        } else {
            long lastModTime = date.getTime();
            if (now - lastModTime < 86400000) {
                out.write("Today");
            } else {
                out.write(dateFormatter.format(date));
            }
        }
        out.write("</td><td>");
        if (file.isDirectory()) {
            out.write(BLANK_PLACEHOLDER);
        } else {
            out.write(Util.readableSize(file.length()));
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
        if (files == null) {
            return dir.getName();
        }

        if (files.length == 1) {
            File entry = new File(dir, files[0]);
            PathAccepter pathAccepter = RuntimeEnvironment.getInstance().getPathAccepter();
            if (pathAccepter.accept(entry) && entry.isDirectory()) {
                return (dir.getName() + "/" + getSimplifiedPath(entry));
            }
        }

        return dir.getName();
    }

    /**
     * Calls
     * {@link #extraListTo(java.lang.String, java.io.File, java.io.Writer, java.lang.String, java.util.List)}
     * with {@code contextPath}, {@code dir}, {@code out}, {@code path},
     * and a list mapped from {@code files}.
     * @param contextPath context path
     * @param dir directory
     * @param out writer
     * @param path path
     * @param files list of files
     * @throws CacheException on error
     * @throws IOException I/O exception
     */
    @VisibleForTesting
    public void listTo(String contextPath, File dir, Writer out, String path, List<String> files)
            throws IOException, CacheException {

        List<DirectoryEntry> filesExtra = createDirectoryEntries(dir, path, files);
        extraListTo(contextPath, dir, out, path, filesExtra);
    }

    /**
     * @param dir the directory to list
     * @param path virtual path of the directory (usually the path name of
     *        <var>dir</var> with the source root directory stripped off).
     * @return list of {@link DirectoryEntry} instances
     * @throws CacheException if history cache operation failed
     */
    public List<DirectoryEntry> createDirectoryEntries(File dir, String path, List<String> files) throws CacheException {
        List<DirectoryEntry> entries = new ArrayList<>(files.size());

        for (String filePath : files) {
            File file = new File(dir, filePath);
            DirectoryEntry entry = new DirectoryEntry(file);
            entries.add(entry);
        }

        // Filter out entries that are not allowed.
        PathAccepter pathAccepter = RuntimeEnvironment.getInstance().getPathAccepter();
        entries.removeIf(entry -> !pathAccepter.accept(entry.getFile()));

        if (entries.isEmpty()) {
            return entries;
        }

        int offset = -1;
        EftarFileReader.FNode parentFNode = null;
        if (desc != null) {
            try {
                parentFNode = desc.getNode(path);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, String.format("cannot get Eftar node for path ''%s''", path), e);
            }
            if (parentFNode != null) {
                offset = parentFNode.getChildOffset();
            }
        }

        boolean fallback = HistoryGuru.getInstance().fillLastHistoryEntries(dir, entries);

        for (DirectoryEntry entry : entries) {
            File child = entry.getFile();
            String filename = child.getName();

            String pathDescription = "";
            try {
                if (offset > 0) {
                    pathDescription = desc.getChildTag(parentFNode, filename);
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, String.format("cannot get path description for '%s'", child), e);
            }
            entry.setPathDescription(pathDescription);

            if (entry.getDate() == null && fallback) {
                long date = child.lastModified();
                entry.setDate(new Date(date));
            }
        }

        return entries;
    }

    /**
     * Write HTML-ized listing of the given directory to the given destination.
     *
     * @param contextPath path used for link prefixes
     * @param dir the directory to list
     * @param out write destination
     * @param path virtual path of the directory (usually the path name of
     *  <var>dir</var> with the source root directory stripped off).
     * @param entries basenames of potential children of the directory to list,
     *  assuming that these were filtered by {@link PathAccepter}.
     * @throws IOException when cannot write to the {@code out} parameter
     * @throws CacheException when failed to get last modified time for files in directory
     */
    public void extraListTo(String contextPath, File dir, Writer out,
                                    String path, @Nullable List<DirectoryEntry> entries) throws IOException {
        // TODO this belongs to a jsp, not here

        boolean entriesWithPathDescriptionsPresent = false;
        if (entries != null) {
            for (DirectoryEntry entry : entries) {
                String pathDescription = entry.getPathDescription();
                if (pathDescription != null && !pathDescription.isEmpty())  {
                    entriesWithPathDescriptionsPresent = true;
                    break;
                }
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
        out.write("<th>#Lines</th>\n");
        out.write("<th>LOC</th>\n");
        if (entriesWithPathDescriptionsPresent) {
            out.write("<th><samp>Description</samp></th>\n");
        }
        out.write("</tr>\n</thead>\n<tbody>\n");

        Format dateFormatter = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());

        // Print the '..' entry even for empty directories.
        if (path.length() != 0) {
            out.write("<tr><td><p class=\"'r'\"/></td><td>");
            out.write("<b><a href=\"..\">..</a></b></td><td></td>");
            printDateSize(out, dir.getParentFile(), null, dateFormatter);
            out.write("</tr>\n");
        }

        if (entries != null) {
            for (DirectoryEntry entry : entries) {
                File child = entry.getFile();
                String filename = child.getName();

                boolean isDir = child.isDirectory();

                out.write("<tr><td>");
                out.write("<p class=\"");
                out.write(isDir ? 'r' : 'p');
                out.write("\"/>");
                out.write("</td><td><a href=\"");
                if (isDir) {
                    String longpath = getSimplifiedPath(child);
                    out.write(Util.uriEncodePath(longpath));
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
                    out.write(Util.uriEncodePath(filename));
                    out.write("\"");
                    if (entry.getDescription() != null) {
                        out.write(" class=\"title-tooltip\"");
                        out.write(" title=\"");
                        out.write(Util.encode(entry.getDescription()));
                        out.write("\"");
                    }
                    out.write(">");
                    out.write(filename);
                    out.write("</a>");
                }
                out.write("</td>");
                Util.writeHAD(out, contextPath, path + filename);
                printDateSize(out, child, entry.getDate(), dateFormatter);
                printNumlines(out, entry, isDir);
                printLoc(out, entry, isDir);
                if (entriesWithPathDescriptionsPresent) {
                    out.write("<td>");
                    out.write(entry.getPathDescription());
                    out.write("</td>");
                }
                out.write("</tr>\n");
            }
        }
        out.write("</tbody>\n</table>");
    }

    private void printNumlines(Writer out, DirectoryEntry entry, boolean isDir)
            throws IOException {
        Long numlines = null;
        String readableNumlines = "";
        NullableNumLinesLOC extra = entry.getExtra();
        if (extra != null) {
            numlines = extra.getNumLines();
        }
        if (numlines != null) {
            readableNumlines = Util.readableCount(numlines, isDir);
        }

        out.write("<td class=\"numlines\">");
        out.write(readableNumlines);
        out.write("</td>");
    }

    private void printLoc(Writer out, DirectoryEntry entry, boolean isDir)
            throws IOException {
        Long loc = null;
        String readableLoc = "";
        NullableNumLinesLOC extra = entry.getExtra();
        if (extra != null) {
            loc = extra.getLOC();
        }
        if (loc != null) {
            readableLoc = Util.readableCount(loc, isDir);
        }

        out.write("<td class=\"loc\">");
        out.write(readableLoc);
        out.write("</td>");
    }
}
