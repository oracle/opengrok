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
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.index;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Filter implements Serializable {
    private static final long serialVersionUID = 3L;

    /** The list of exact filenames */
    private final Set<String> filenames;
    /** The list of filenames with wildcards */
    private final List<Pattern> patterns;
    /** The list of paths */
    private final List<String> paths;
    /**
     * The full list of all patterns. This list will be saved in the
     * configuration file (if used)
     */
    private final List<String> items;

    public Filter() {
        filenames = new HashSet<>();
        patterns = new ArrayList<>();
        paths = new ArrayList<>();
        items = new PatternList(this);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }


    /**
     * Get the complete list of items that would be matched by this matcher
     * @return a list of all wildcards, exact lists and paths that this filter
     *         contains
     */
    public List<String> getItems() {
        return items;
    }

    /**
     * Specify a new filter to use
     * @param item the new filter
     */
    public void setItems(List<String> item) {
        clear();
        for (String s : item) {
            add(s);
        }
    }

    /**
     * Add a pattern to the list of patterns
     * @param pattern the pattern to filename
     */
    public void add(String pattern) {
        if (!items.contains(pattern)) {
            items.add(pattern);
        }
    }

    /**
     * Remove all installed patterns from the list of files to filename
     */
    public void clear() {
        patterns.clear();
        filenames.clear();
        paths.clear();
        items.clear();
    }

    /**
     * Does the file match any of the filenames, patterns or paths ?
     * @param file the file to check
     * @return true if this file matches, false otherwise
     */
    public boolean match(File file) {
        String fileName = file.getName(); // basename
        String absolute = file.getAbsolutePath();

        if (filenames.contains(fileName)) {
            return true;
        }
        
        for (Pattern p : patterns) {
            // Try to match the basename first.
            Matcher m = p.matcher(fileName);
            if (m.matches()) {
                return true;
            }
            // Try the full path next.
            if (p.pattern().contains("/")) {
                m = p.matcher(absolute);
                if (m.matches()) {
                    return true;
                }
            }
        }

        for (String s : paths) {
            if (absolute.endsWith(s)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Does the file name match any of the filenames, patterns or paths ?
     * @param name the name of the file to check
     * @return true if this pathname matches, false otherwise
     */
    public boolean match(String name) {
        return match(new File(name));
    }

    /**
     * Add a pattern to the correct list of internal filters to match
     *
     * @param pattern the pattern to add
     */
    private void addPattern(String pattern) {
        if (pattern.contains("*") || pattern.contains("?")) {
            patterns.add(compilePattern(pattern));
        } else if (pattern.contains(File.separator)) {
            if (pattern.charAt(0) == File.separatorChar) {
                paths.add(pattern);
            } else {
                paths.add(File.separator + pattern);
            }
        } else {
            filenames.add(pattern);
        }
    }

    /**
     * Convert the glob pattern (examples: *.c, *.?xx) to a regular expression
     * and compile it.
     *
     * @param pattern a pattern to match file names against
     * @return a compiled regular expression representing the pattern
     */
    private Pattern compilePattern(String pattern) {
        // Build the regex by replacing "*" with ".*" and "?" with ".". All
        // other characters should be quoted to ensure exact match.
        StringBuilder regex = new StringBuilder();
        int pos = 0;
        String[] components = pattern.split("[*?]");
        for (String str : components) {
            if (str.length() > 0) {
                // Quote the characters up to next wildcard or end of string.
                regex.append(Pattern.quote(str));
                pos += str.length();
            }
            if (pos < pattern.length()) {
                // Replace wildcard with equivalent regular expression.
                if (pattern.charAt(pos) == '*') {
                    regex.append(".*");
                } else {
                    assert pattern.charAt(pos) == '?';
                    regex.append('.');
                }
                pos++;
            }
        }

        // Compile the regex.
        return Pattern.compile(regex.toString());
    }

    public static class PatternList extends ArrayList<String> {
        private final Filter owner;

        public PatternList(Filter owner) {
            this.owner = owner;
        }

        @Override
        public boolean add(String pattern) {
            boolean ret = super.add(pattern);
            if (ret) {
                owner.addPattern(pattern);
            }
            return ret;
        }
    }
}
