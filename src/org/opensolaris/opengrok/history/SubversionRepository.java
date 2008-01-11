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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.tigris.subversion.javahl.BlameCallback;
import org.tigris.subversion.javahl.ClientException;
import org.tigris.subversion.javahl.Info;
import org.tigris.subversion.javahl.LogMessage;
import org.tigris.subversion.javahl.Revision;
import org.tigris.subversion.javahl.SVNClient;

/**
 * Access to a Subversion repository. 
 */
public class SubversionRepository implements ExternalRepository {

    private File directory;
    private String directoryName;
    private boolean verbose;
    private boolean ignored;

    /**
     * Creates a new instance of SubversionRepository
     */
    public SubversionRepository() {
    }

    /**
     * Creates a new instance of SubversionRepository
     * @param directory The directory containing the .svn-subdirectory
     */
    public SubversionRepository(String directory) {
        this.directory = new File(directory);
        directoryName = this.directory.getAbsolutePath();

        if (!RuntimeEnvironment.getInstance().isRemoteScmSupported()) {
            // try to figure out if I should ignore this repository
            try {
                SVNClient client = new SVNClient();
                Info info = client.info(directory);
                if (!info.getUrl().startsWith("file")) {
                    if (RuntimeEnvironment.getInstance().isVerbose()) {
                        System.out.println("Skipping history from remote repository: <" + directory + ">");
                    }
                    ignored = true;
                }
            } catch (Exception e) {

            }
        }
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
        InputStream ret = null;
        
        Revision revision = Revision.WORKING;
        
        if (rev != null) {
            try {
                revision = Revision.getInstance(Long.parseLong(rev));
            } catch (NumberFormatException exp) {
                System.err.println("Failed to retrieve rev (" + rev + "): Not a valid Subversion revision format");
                exp.printStackTrace();
                return null;
            }
        }

        SVNClient client = new SVNClient();
        try {
            Info info = client.info(directoryName);
            String wcUrl = info.getUrl();

            String svnPath = parent + "/" + basename;

            // erase the working copy from the path to get the fragment
            svnPath = svnPath.substring(directoryName.length());

            ret = new ByteArrayInputStream(client.fileContent(wcUrl + svnPath, revision));
        } catch (ClientException ex) {
            System.err.println("Failed to retrieve rev (" + rev + "): " + ex.toString());
            ex.printStackTrace();
        }        
        
        return ret;
    }

    public Class<? extends HistoryParser> getHistoryParser() {
        return SubversionHistoryParser.class;
    }

    public Class<? extends HistoryParser> getDirectoryHistoryParser() {
        return SubversionHistoryParser.class;
    }
    
    /**
     * Get the name of the root directory for this repository
     * @return the name of the directory containing the .svn subdirectory
     */
    public String getDirectoryName() {
        return directoryName;
    }

    /**
     * Specify the name of the root directory for this repository
     * @param directoryName the new name of the directory containing the .svn 
     *        subdirectory
     */
    public void setDirectoryName(String directoryName) {
        this.directoryName = directoryName;
        this.directory = new File(this.directoryName);
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    public void createCache() throws IOException, ParseException {
        if (ignored) {
            if (RuntimeEnvironment.getInstance().isVerbose()) {
                System.out.println("Skipping Subversion History Cache for " + directory);
                System.out.flush();
            }
            return;
        }

        SubversionHistoryParser parser = new SubversionHistoryParser();
        History history = null;

        try {
            history = parser.parse(directory, this);
        } catch (Exception e) {
            System.err.println("Failed to get info for " + directory);
            e.printStackTrace();
        }

        if (history != null && history.getHistoryEntries() != null) {
            HashMap<String, ArrayList<HistoryEntry>> map = new HashMap<String, ArrayList<HistoryEntry>>();
            for (HistoryEntry e : history.getHistoryEntries()) {
                for (String s : e.getFiles()) {
                    ArrayList<HistoryEntry> list = map.get(s);
                    if (list == null) {
                        list = new ArrayList<HistoryEntry>();
                        list.add(e);
                        map.put(s, list);
                    } else {
                        list.add(e);
                    }
                }
            }

            for (Map.Entry<String, ArrayList<HistoryEntry>> e : map.entrySet()) {
                for (HistoryEntry ent : e.getValue()) {
                    ent.strip();
                }

                History hist = new History();
                hist.setHistoryEntries(e.getValue());
                HistoryCache.writeCacheFile(e.getKey(), hist);
            }
        }
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

    public boolean supportsAnnotation() {
        return true;
    }

    public boolean isCacheable() {
        return true;
    }
    
    public boolean fileHasHistory(File file) {
        // TODO: Research how to cheaply test if a file in a given
        // SVN repo has history.  If there is a cheap test, then this
        // code can be refined, boosting performance.
        return true;
    }

    public void update() throws Exception {
        Process process = null;
        String command = System.getProperty("org.opensolaris.opengrok.history.Subversion", "svn");

        try {
            process = Runtime.getRuntime().exec(new String[] {command, "update"}, null, directory);
            boolean interrupted;
            do {
                interrupted = false;
                try {
                    if (process.waitFor() != 0) {
                        return;
                    }
                } catch (InterruptedException exp) {
                    interrupted = true;
                }
            } while (interrupted);
        } finally {
            
            // is this really the way to do it? seems a bit brutal...
            try {
                process.exitValue();
            } catch (IllegalThreadStateException e) {
                process.destroy();
            }
        }
    }
}
