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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;

/**
 * An interface for an external repository. 
 *
 * @author Trond Norbye
 */
public interface ExternalRepository {
    /**
     * Get a parser capable of getting history log elements from this repository.
     * @return a specialized parser for this kind of repository
     */
    Class<? extends HistoryParser> getHistoryParser();
    
    /**
     * Get an input stream that I may use to read a speciffic version of a
     * named file.
     * @param parent the name of the directory containing the file
     * @param basename the name of the file to get
     * @param rev the revision to get
     * @return An input stream containing the correct revision.
     */
    public InputStream getHistoryGet(String parent, String basename, String rev);

    /**
     * Checks whether this parser can annotate files.
     *
     * @return <code>true</code> if annotation is supported
     */
    public boolean supportsAnnotation(); 

    /**
     * Annotate the specified revision of a file.
     *
     * @param file the file to annotate
     * @param revision revision of the file
     * @return an <code>Annotation</code> object
     * @throws java.lang.Exception if an error occurs
     */
    public Annotation annotate(File file, String revision) throws Exception;

    /**
     * Check whether the parsed history should be cached.
     *
     * @return <code>true</code> if the history should be cached
     */
    boolean isCacheable();
    
    /**
     * Create a history log cache for all of the files in this repository.
     * Some SCM's have a more optimal way to query the log information, so
     * the concrete repository could implement a smarter way to generate the
     * cache instead of creating it for each file beeing accessed.
     * @throws IOException if an error occurs while creating the cache
     * @throws ParseException if an error occurs while parsing the log information.
     */
    public void createCache() throws IOException, ParseException;
    
    /**
     * Update the content in this repository by pulling the changes from the
     * upstream repository..
     * @throws Exception if an error occurs.
     */
    public void update() throws Exception;
    
}
