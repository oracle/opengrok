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
 * Copyright (c) 2011, Jens Elkner.
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.web;

import java.util.Map;
import java.util.TreeMap;

/**
 * URL Prefixes usually tied to a certain servlet.
 *
 * @author  Jens Elkner
 * @version $Revision$
 */
public enum Prefix {
    /** Unknown prefix. */
    UNKNOWN(""),
    /** A cross reference. */
    XREF_P("/xref"),
    /** A show cross reference, i.e. add Line and Navigation toggle button in the menu bar. */
    XREF_S("/xr"),
    /** show more lines. If a search result set for a file matches more lines
     * than a given limit (default: 10), only the first <i>limit</i> lines gets
     * shown as an "[all ...]" link, which can be used to show all matching
     * lines. The servlet path of this link starts with this prefix. */
    MORE_P("/more"),
    /** Reserved (not used). */
    MORE_S("/mo"),
    /** Diff to previous version (link prefix). */
    DIFF_P("/diff"),
    /** Reserved (not used). */
    DIFF_S("/di"),
    /** Reserved (not used). */
    HIST_P("/hist"),
    /** Reserved (not used). */
    HIST_S("/hi"),
    /** Show the history for a file (link prefix). */
    HIST_L("/history"),
    /** RSS XML Feed of latest changes (link prefix). */
    RSS_P("/rss"),
    /** Download file (link prefix). */
    DOWNLOAD_P("/download"),
    /** Raw file display (link prefix). */
    RAW_P("/raw"),
    /** Full blown search from main page or top bar (link prefix). */
    SEARCH_P("/search"),
    /** Search from cross reference, can lead to direct match (which opens
     * directly) or to a matches Summary page. */
    SEARCH_R("/s"),
    /** opensearch description page. */
    SEARCH_O("/opensearch"),
    /** Related source file or directory not found/unavailable/ignored. */
    NOT_FOUND("/enoent"),
    /** Misc error occurred. */
    ERROR("/error"),
    /** RESTful API. */
    REST_API("/api"),
    /** Monitoring. */
    METRICS("/metrics"),
    /** CSS and images. */
    STATIC("/default"),
    /** JavaScript. */
    JS("/js");

    private final String prefix;
    Prefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Get the string used as prefix.
     * @return the prefix
     */
    @Override
    public String toString() {
        return prefix;
    }

    // should be sufficient for now
    private static final Map<String, Prefix> lookupTable;
    static {
        lookupTable = new TreeMap<>();
        for (Prefix p : Prefix.values()) {
            lookupTable.put(p.toString(), p);
        }
    }

    /**
     * Get the prefix of the given path.
     * @param servletPath  path to check
     * @return {@link Prefix#UNKNOWN} if <var>path</var> is {@code null} or has
     *  no or unknown prefix, the corresponding prefix otherwise.
     * @see #toString()
     */
    public static Prefix get(String servletPath) {
        if (servletPath == null || servletPath.length() < 2 || servletPath.charAt(0) != '/') {
            return UNKNOWN;
        }
        int idx = servletPath.indexOf('/', 1);
        String pathPrefix = (idx == -1) ?
                servletPath : servletPath.substring(0, idx);
        Prefix p = lookupTable.get(pathPrefix);
        return p == null ? UNKNOWN : p;
    }
}
