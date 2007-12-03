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
import java.util.Date;
import org.tigris.subversion.javahl.BlameCallback;
import org.tigris.subversion.javahl.LogMessage;
import org.tigris.subversion.javahl.Revision;
import org.tigris.subversion.javahl.SVNClient;

/**
 * Access to a Mercurial repository.
 * 
 */
public class SubversionRepository implements ExternalRepository {
    private File directory;
    private String directoryName;
    private boolean verbose;
    
    /**
     * Creates a new instance of MercurialRepository
     */
    public SubversionRepository() { }
    
    /**
     * Creates a new instance of MercurialRepository
     * @param directory The directory containing the .hg-subdirectory
     */
    public SubversionRepository(String directory) {
        this.directory = new File(directory);
        directoryName = this.directory.getAbsolutePath();
    }
        
    /**
     * Use verbose log messages, or just the summary
     * @return true if verbose log messages are used for this repository
     */
    public boolean isVerbose() {
        return verbose;
    }
        
    /**
     * Specify if verbose log messages or just the summary should be used
     * @param verbose set to true if verbose messages should be used
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
    
    
    public InputStream getHistoryGet(String parent, String basename, String rev) {
        return new SubversionGet(parent, basename, rev);
    }
    
    public Class<? extends HistoryParser> getHistoryParser() {
        return SubversionHistoryParser.class;
    }

    /**
     * Get the name of the root directory for this repository
     * @return the name of the directory containing the .hg subdirectory
     */
    public String getDirectoryName() {
        return directoryName;
    }
    
    /**
     * Specify the name of the root directory for this repository
     * @param directoryName the new name of the directory containing the .hg 
     *        subdirectory
     */
    public void setDirectoryName(String directoryName) {
        this.directoryName = directoryName;
        this.directory = new File(this.directoryName);
    }
    
    public void createCache() throws IOException, ParseException {
    }

    public Annotation annotate(File file, String revision) throws Exception {
        SVNClient client = new SVNClient();

        // we need to find the first revision where the file appeared at this location
        // in order to find out if it should be disabled because of a copy
        LogMessage[] messages =
                client.logMessages(file.getPath(), Revision.START, Revision.BASE, true, false, 1);

        final long oldestRevOnThisPath = (messages != null && messages.length > 0) ? messages[0].getRevisionNumber() : 0;

        final Annotation annotation = new Annotation(file.getName());
        BlameCallback callback = new BlameCallback() {

                    public void singleLine(Date changed, long revision,
                            String author, String line) {
                        annotation.addLine(Long.toString(revision), author, oldestRevOnThisPath <= revision);
                    }
                };

        Revision rev = (revision == null) ? Revision.BASE : Revision.getInstance(Long.parseLong(revision));
        client.blame(file.getPath(), Revision.START, rev, callback);
        return annotation;

    }
}


