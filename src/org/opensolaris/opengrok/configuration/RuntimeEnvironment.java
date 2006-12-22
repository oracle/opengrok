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
 * Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.configuration;

import java.io.File;
import java.io.IOException;

/**
 * The RuntimeEnvironment class is used as a placeholder for the current
 * configuration this execution context (classloader) is using. 
 */
public class RuntimeEnvironment {
    private String sourceRoot;
    private String dataRoot;
    private String urlPrefix;
    private String ctags;
    boolean useHistoryCache;
    int historyReaderTimeLimit;
    
    
    public String getUrlPrefix() {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    public String getCtags() {
        return ctags;
    }

    public void setCtags(String ctags) {
        this.ctags = ctags;
    }

    public int getHistoryReaderTimeLimit() {
        return historyReaderTimeLimit;
    }

    public void setHistoryReaderTimeLimit(int historyReaderTimeLimit) {
        this.historyReaderTimeLimit = historyReaderTimeLimit;
    }

    public boolean useHistoryCache() {
        return useHistoryCache;
    }

    public void setUseHistoryCache(boolean useHistoryCache) {
        this.useHistoryCache = useHistoryCache;
    }
    

    
    
    private static RuntimeEnvironment instance = new RuntimeEnvironment();
    
    public static RuntimeEnvironment getInstance() {
        return instance;
    }
    
    /**
     * Creates a new instance of RuntimeEnvironment
     */
    private RuntimeEnvironment() {
        useHistoryCache = true;
        historyReaderTimeLimit = 300;
    }    
    
    public File getDataRootFile() {
        return new File(dataRoot);
    }

    public File getSourceRootFile() {
        return new File(sourceRoot);
    }

    public void setDataRoot(File data) throws IOException {
        dataRoot = data.getCanonicalPath();
    }

    public void setSourceRoot(File source) throws IOException {
        sourceRoot = source.getCanonicalPath();
    }

    public String getDataRootPath() {
        return dataRoot;
    }

    public String getSourceRootPath() {
        return sourceRoot;
    }

    public void setDataRoot(String dataRoot) {
        this.dataRoot = dataRoot;
    }

    public void setSourceRoot(String sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

}
