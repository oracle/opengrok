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
package org.opensolaris.opengrok.index;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.oro.io.GlobFilenameFilter;

/**
 * This class maintains a list of file names (like "cscope.out"), SRC_ROOT relative
 * file paths (like "usr/src/uts" or "usr/src/Makefile"), and glob patterns
 * (like .make.*) which opengrok should ignore.
 *
 * @author Chandan
 */
public class IgnoredNames {
    private static final String[] defaultPatterns = {
        "SCCS",
        "CVS",
        "RCS",
        "cscope.in.out",
        "cscope.out.po",
        "cscope.out.in",
        "cscope.po.out",
        "cscope.po.in",
        "cscope.files",
        "cscope.out",
        "Codemgr_wsdata",
        ".cvsignore",
        "CVSROOT",
        "TAGS",
        "tags",
        ".svn",
        ".git",
        ".hg",
        ".hgtags",
        ".bzr",
        ".p4config",
        "*~",
        "deleted_files",
        ".make.*",
        ".del-*"
    };
    
    /** The list of exact filenames to ignore */     
    private Set<String> ignore;
    /** The list of filenames with wildcards to ignore */
    private List<FileFilter> patterns;
    /** The list of paths that should be ignored */
    private List<String> path;
    /** The full list of all patterns. This list will be saved in the
     * configuration file (if used)
     */
    private List<String> ignoredPatterns;
    
    public IgnoredNames() {
        ignore = new HashSet<String>();
        patterns = new ArrayList<FileFilter>();
        path = new ArrayList<String>();
        ignoredPatterns = new PatternList(this);
        addDefaultPatterns();
    }
    
    public List<String> getIgnoredPatterns() {
        return ignoredPatterns;
    }
    
    public void setIgnoredPatterns(List<String> ignoredPatterns) {
        clear();
        for (String s : ignoredPatterns) {
            add(s);
        }
    }
    
    /**
     * Add a pattern to the list of patterns of filenames to ignore
     * @param pattern the pattern to ignore
     */
    public void add(String pattern) {
        if (!ignoredPatterns.contains(pattern)) {
            ignoredPatterns.add(pattern);
        }
    }
    
    /**
     * Remove all installed patterns from the list of files to ignore
     */
    public void clear() {
        patterns.clear();
        ignore.clear();
        path.clear();
        ignoredPatterns.clear();
    }
    
    /**
     * Should the file be ignored or not?
     * @param file the file to check
     * @return true if this file should be ignored, false otherwise
     */
    public boolean ignore(File file) {
        boolean ret = false;

        if (ignore.contains(file.getName())) {
            ret = true;
        } else {
            for (FileFilter fe : patterns) {
                if (fe.accept(file)) {
                    ret = true;
                    break;
                }
            }
        }
        
        if (!ret) {
            String absolute = file.getAbsolutePath();
            for (String s : path) {
                if (absolute.endsWith(s)) {
                    ret = true;
                    break;
                }
            }
        }
        
        return ret;        
    }
    
    /**
     * Should the file be ignored or not?
     * @param name the name of the file to check
     * @return true if this pathname should be ignored, false otherwise
     */
    public boolean ignore(String name) {
        return ignore(new File(name));
    }

    public void addDefaultPatterns() {
        for (String s : defaultPatterns) {
            add(s);
        }
    }    
    
    private void addPattern(String pattern) {
        if (pattern.indexOf('*') != -1 || pattern.indexOf('?') != -1) {
            patterns.add(new GlobFilenameFilter(pattern));
        } else if (pattern.indexOf(File.separatorChar) != -1) {
            if (pattern.charAt(0) == File.separatorChar) {
                path.add(pattern);
            } else {
                path.add(File.separator + pattern);
            }
        } else {
            ignore.add(pattern);
        }
    }
    
    /**
     * During the load of the configuration file, the framework will add
     * entries to the ignored pattern list. Since I use them in different
     * lists, I need to detect when an object is beeing added to this list 
     * (So I may populate it to the correct list as well)
     */
    public class PatternList extends ArrayList<String> {
        private IgnoredNames owner;
        
        public PatternList(IgnoredNames owner) {
            this.owner = owner;
        }
        

        public boolean add(String pattern) {
            boolean ret = super.add(pattern);
            if (ret) {
                owner.addPattern(pattern);
            }
            return ret;
        }
    }
}
