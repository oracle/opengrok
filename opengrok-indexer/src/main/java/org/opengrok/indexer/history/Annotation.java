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
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import org.jetbrains.annotations.TestOnly;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Color;
import org.opengrok.indexer.util.LazilyInstantiate;
import org.opengrok.indexer.util.RainbowColorGenerator;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Class representing file annotation, i.e., revision and author for the last
 * modification of each line in the file.
 */
public class Annotation {

    private static final Logger LOGGER = LoggerFactory.getLogger(Annotation.class);

    AnnotationData annotationData;

    private final Map<String, String> desc = new HashMap<>(); // maps revision to description
    private final Map<String, Integer> fileVersions = new HashMap<>(); // maps revision to file version

    private final LazilyInstantiate<Map<String, String>> colors = LazilyInstantiate.using(this::generateColors);

    public Annotation(String filename) {
        annotationData = new AnnotationData(filename);
    }

    public Annotation(AnnotationData annotationData) {
        this.annotationData = annotationData;
    }

    void addLine(String revision, String author, boolean enabled) {
        annotationData.addLine(revision, author, enabled);
    }

    public String getFilename() {
        return annotationData.getFilename();
    }

    public void setRevision(String revision) {
        annotationData.setRevision(revision);
    }

    /**
     * @return revision this annotation was generated for
     */
    public String getRevision() {
        return annotationData.getRevision();
    }

    /**
     * Returns the size of the file (number of lines).
     *
     * @return number of lines
     */
    public int size() {
        return annotationData.size();
    }

    /**
     * Gets the revision for the last change to the specified line.
     *
     * @param line line number (counting from 1)
     * @return revision string, or an empty string if there is no information
     * about the specified line
     */
    public String getRevision(int line) {
        return annotationData.getRevision(line);
    }

    /**
     * Gets all revisions that are in use, first is the lowest one (sorted using natural order).
     *
     * @return list of all revisions the file has
     */
    public Set<String> getRevisions() {
        return annotationData.getRevisions();
    }

    @TestOnly
    Set<String> getAuthors() {
        return annotationData.getAuthors();
    }

    /**
     * Gets the author who last modified the specified line.
     *
     * @param line line number (counting from 1)
     * @return author, or an empty string if there is no information about the
     * specified line
     */
    public String getAuthor(int line) {
        return annotationData.getAuthor(line);
    }

    public int getWidestRevision() {
        return annotationData.getWidestRevision();
    }

    public int getWidestAuthor() {
        return annotationData.getWidestAuthor();
    }

    /**
     * Gets the enabled state for the last change to the specified line.
     *
     * @param line line number (counting from 1)
     * @return true if the xref for this revision is enabled, false otherwise
     */
    public boolean isEnabled(int line) {
        return annotationData.isEnabled(line);
    }

    void addDesc(String revision, String description) {
        desc.put(revision, description);
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
        return fileVersions.getOrDefault(revision, 0);
    }

    /**
     * @return Count of revisions on this file.
     */
    public int getFileVersionsCount() {
        return fileVersions.size();
    }

    /**
     * Return the color palette for the annotated file.
     *
     * @return map of (revision, css color string) for each revision in {@code getRevisions()}
     * @see #generateColors()
     */
    public Map<String, String> getColors() {
        return colors.get();
    }

    /**
     * Generate the color palette for the annotated revisions.
     * <p>
     * First, take into account revisions which are tracked in history fields
     * and compute their color. Secondly, use all other revisions in order
     * which is undefined and generate the rest of the colors for them.
     *
     * @return map of (revision, css color string) for each revision in {@code getRevisions()}
     * @see #getRevisions()
     */
    private Map<String, String> generateColors() {
        List<Color> colors = RainbowColorGenerator.getOrderedColors();

        Map<String, String> colorMap = new HashMap<>();
        final List<String> revisions =
                getRevisions()
                        .stream()
                        /*
                         * Greater file version means more recent revision.
                         * 0 file version means unknown revision (untracked by history entries).
                         *
                         * The result of this sort is:
                         * 1) known revisions sorted from most recent to least recent
                         * 2) all other revisions in non-determined order
                         */
                        .sorted(Comparator.comparingInt(this::getFileVersion).reversed())
                        .collect(Collectors.toList());

        final int nColors = colors.size();
        final double colorsPerBucket = (double) nColors / getRevisions().size();

        revisions.forEach(revision -> {
            final int lineVersion = getRevisions().size() - getFileVersion(revision);
            final double bucketTotal = colorsPerBucket * lineVersion;
            final int bucketIndex = (int) Math.max(
                    Math.min(Math.floor(bucketTotal), nColors - 1.0), 0);
            Color color = colors.get(bucketIndex);
            colorMap.put(revision, String.format("rgb(%d, %d, %d)", color.red, color.green, color.blue));
        });

        return colorMap;
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
        for (AnnotationLine annotationLine : annotationData.getLines()) {
            sw.append(annotationLine.getRevision());
            sw.append("|");
            sw.append(annotationLine.getAuthor());
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
