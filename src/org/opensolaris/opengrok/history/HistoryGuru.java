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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)HistoryGuru.java 1.2     06/02/22 SMI"
 */
package org.opensolaris.opengrok.history;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import org.opensolaris.opengrok.web.Util;

/**
 * The HistoryGuru is used to implement an transparent layer to the various
 * source control systems.
 *
 * @author Chandan
 */
public class HistoryGuru {
    /** The one and only instance of the HistoryGuru */
    private static HistoryGuru instance = new HistoryGuru();
    /** Unknown version control system for last file */
    private final int UNKNOWN = 0;
    /** RCS was used by the last file */
    private final int RCS = 1;
    /** SCCS was used by the last file */
    private final int SCCS = 2;
    /** Subversion was used by the last file */
    private final int SVN = 3;
    /** The last file was located in an "external" repository */
    private final int EXTERNAL = 4;
    /** Method used on the last file */
    private int previousFile;
    /** Is JavaSVN available? */
    private boolean svnAvailable;
    private boolean initializedSvn;
    private static final String sccslabel = "/SCCS/s.";
    private static final String svnlabel = ".svn";
    
    private boolean isSvnAvailable() {
        if (!initializedSvn) {
            initializedSvn = true;
            try {
                if (Class.forName("org.tigris.subversion.javahl.SVNClient") != null) {
                    svnAvailable = true;
                }
            } catch (ClassNotFoundException ex) {
                /* EMPTY */
                ;
            } catch (UnsatisfiedLinkError ex) {
                System.err.println("Failed to initialize Subversion library: " + ex);
            }
        }
        return svnAvailable;
    }
    
    /**
     * Creates a new instance of HistoryGuru, and try to set the default
     * source control system.
     * @todo Set the revision system according to the users preferences, and call the various setups..
     */
    private HistoryGuru() {
        svnAvailable = false;
        previousFile = UNKNOWN;
        externalRepositories = new HashMap<String, ExternalRepository>();
    }
    
    /**
     * Get the one and only instance of the HistoryGuru
     * @return the one and only HistoryGuru instance
     */
    public static HistoryGuru getInstance()  {
        return instance;
    }
    
    /**
     * Try to guess the correct history reader to use. Create an RCS, SCCS or Subversion
     * HistoryReader if it looks like the file is managed by the appropriate
     * revision control system.
     *
     * @param parent The directory containing this file
     * @param basename The name of the file to get the history reader for
     * @throws java.io.IOException If an error occurs while trying to access the filesystem
     * @return A HistorReader that may be used to read out history data for a named file
     */
    private HistoryReader guessHistoryReader(String parent, String basename) throws IOException {
        HistoryReader hr = null;
        String rcspath = Util.getRCSFile(parent, basename);
        if (rcspath != null && (new File(rcspath)).exists()) {
            hr = new RCSHistoryReader(rcspath);
            previousFile = RCS;
        } else {
            File sfile = new File(parent + sccslabel + basename);
            if (sfile.canRead()) {
                hr = new SCCSHistoryReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(sfile))));
                previousFile = SCCS;
            } else {
                File svn = new File(parent, svnlabel);
                
                if (svn.exists() && isSvnAvailable()) {
                    try {
                        hr = new SubversionHistoryReader(parent, basename);
                        previousFile = SVN;
                    } catch (Exception e) {
                        ;
                    }
                } else {
                    hr = lookupHistoryReader(parent, basename);
                    if (hr != null) {
                        previousFile = EXTERNAL;
                    }
                }
            }
        }
        
        if (hr == null) {
            previousFile = UNKNOWN;
        }
        return hr;
    }
    
    
    /**
     * Get the appropriate history reader for the file specified by parent and basename.
     * If configured, it will try to use the configured system. If the file is under another
     * revision control system, it will try to guess the correct system.
     *
     * @param parent The directory containing this file
     * @param basename The name of the file to get the history reader for
     * @throws java.io.IOException If an error occurs while trying to access the filesystem
     * @return A HistorReader that may be used to read out history data for a named file
     */
    public HistoryReader getHistoryReader(String parent, String basename) throws IOException {
        HistoryReader hr = null;
        String rcspath;
        File sfile;
        
        switch (previousFile) {
            case EXTERNAL :
                hr = lookupHistoryReader(parent, basename);
                break;
                
            case SVN :
                sfile = new File(parent, svnlabel);
                if (sfile.exists()) {
                    try {
                        hr = new SubversionHistoryReader(parent, basename);
                    } catch (Exception e) { ; }
                }
                break;
                
            case RCS :
                rcspath = Util.getRCSFile(parent, basename);
                if (rcspath != null && (new File(rcspath)).exists()) {
                    hr = new RCSHistoryReader(rcspath);
                }
                break;
                
            case SCCS :
                sfile = new File(parent + sccslabel + basename);
                try{
                    hr = new SCCSHistoryReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(sfile))));
                } catch (IOException e) { ; }
                break;
                
            default:
                ;
        }
        
        if (hr == null) {
            // I did not find a match for the specified system. try to guess..
            hr = guessHistoryReader(parent, basename);
        }
        
        return hr;
    }
    
    /**
     * Get a named revision of the specified file. Try to guess out the source
     * control system that is used.
     *
     * @param parent The directory containing the file
     * @param basename The name of the file
     * @param rev The revision to get
     * @throws java.io.IOException If an error occurs while reading out the version
     * @return An InputStream containing the named revision of the file.
     */
    public InputStream guessGetRevision(String parent, String basename, String rev) throws IOException {
        InputStream in = null;
        String rcspath = Util.getRCSFile(parent, basename);
        if (rcspath != null) {
            in = new BufferedInputStream(new RCSget(rcspath, rev));
            previousFile = RCS;
        } else {
            File history = new File(parent + sccslabel + basename);
            if(history.canRead()) {
                in = new BufferedInputStream(new SCCSget(new FileInputStream(history), rev));
                in.mark(32);
                in.read();
                in.reset();
                previousFile = RCS;
            } else {
                File svn = new File(parent, svnlabel);
                if (svn.exists() && isSvnAvailable()) {
                    in = new BufferedInputStream(new SubversionGet(parent, basename, rev));
                    previousFile = SVN;
                } else {
                    in = lookupHistoryGet(parent, basename, rev);
                }
            }
        }
        return in;
    }
    
    /**
     * Get a named revision of the specified file.
     * @param parent The directory containing the file
     * @param basename The name of the file
     * @param rev The revision to get
     * @throws java.io.IOException If an error occurs while reading out the version
     * @return An InputStream containing the named revision of the file.
     */
    public InputStream getRevision(String parent, String basename, String rev) throws IOException {
        InputStream in = null;
        String rcspath;
        File history;
        
        switch (previousFile) {
            case RCS :
                rcspath = Util.getRCSFile(parent, basename);
                if (rcspath != null) {
                    in = new BufferedInputStream(new RCSget(rcspath, rev));
                }
                break;
                
            case SCCS :
                history = new File(parent + sccslabel + basename);
                if(history.canRead()) {
                    in = new BufferedInputStream(new SCCSget(new FileInputStream(history), rev));
                    in.mark(32);
                    in.read();
                    in.reset();
                }
                break;
                
            case SVN :
                history = new File(parent, svnlabel);
                if (history.exists()) {
                    in = new BufferedInputStream(new SubversionGet(parent, basename, rev));
                }
                break;
            case EXTERNAL :
                in = lookupHistoryGet(parent, basename, rev);
                break;
                
            default:
                ;
        }
        
        if (in == null) {
            in = guessGetRevision(parent, basename, rev);
        }
        
        return in;
    }
    
    /**
     * Does this directory contain files with source control information?
     * @param parent The name of the directory
     * @return true if the files in this directory have associated revision history
     */
    public boolean hasHistory(String parent) {
        boolean ret = false;
        if ((new File(parent + "/SCCS")).isDirectory() ||
                (new File(parent + "/CVS")).isDirectory() ||
                (new File(parent, svnlabel)).isDirectory() ||
                (getRepository(parent) != null)) {
            ret = true;
        }
        
        return ret;
    }
    
    public static void main(String[] args) {
        try{
            File f = new File(args[0]);
            String parent = f.getParent();
            String basename = f.getName();
            
            System.out.println("-----Reading comments as a reader");
            HistoryReader hr = HistoryGuru.getInstance().getHistoryReader(parent, basename);
            BufferedReader rr = new BufferedReader(hr);
            int c;
            BufferedOutputStream br = new BufferedOutputStream(System.out);
            while((c = rr.read()) != -1) {
                br.write((char)c);
            }
            br.flush();
            
            System.out.println("-----Reading comments as lines");
            hr = HistoryGuru.getInstance().getHistoryReader(parent, basename);
            while(hr.next()) {
                System.out.println(hr.getLine() + "----------------------");
            }
            hr.close();
            
            System.out.println("-----Reading comments structure");
            hr = HistoryGuru.getInstance().getHistoryReader(parent, basename);
            while(hr.next()) {
                System.out.println("REV  = " + hr.getRevision() + "\nDATE = "+ hr.getDate() + "\nAUTH = " +
                        hr.getAuthor() + "\nLOG  = " + hr.getComment() + "\nACTV = " + hr.isActive() + "\n-------------------");
            }
            hr.close();
        } catch (Exception e) {
            System.err.println("Exception " + e + "\nUsage: HistoryGuru file");
            e.printStackTrace();
        }
    }
    
    public void addExternalRepositories(File[] files) {
        // Check if this directory contain a file named .hg
        for (int ii = 0; ii < files.length; ++ii) {
            if (files[ii].getName().equalsIgnoreCase(".hg")) {
                try {
                    String s = files[ii].getParentFile().getCanonicalPath();
                    System.out.println("Adding Mercurial repository: <" + s + ">");
                    MercurialRepository rep = new MercurialRepository(s);
                    addExternalRepository(rep, s);
                    return ;
                } catch (IOException exp) {
                    System.err.println("Failed to get canonical path for " + files[ii].getName() + ": " + exp.getMessage());
                    System.err.println("Repository will be ignored...");
                    exp.printStackTrace(System.err);
                }
            }
        }
        
        // Nope, search it's sub-dirs
        for (int ii = 0; ii < files.length; ++ii) {
            if (files[ii].isDirectory()) {
                addExternalRepositories(files[ii].listFiles());
            }
        }
    }
    
    public void addExternalRepository(ExternalRepository rep, String path) {
        externalRepositories.put(path, rep);
    }
    
    private ExternalRepository getRepository(String path) {
        ExternalRepository ret = null;
        while (path != null) {
            ExternalRepository r = externalRepositories.get(path);
            if (r != null) {
                ret = r;
                break;
            }
            path = (new File(path)).getParent();
        }
        return ret;
    }
    
    HistoryReader lookupHistoryReader(String parent, String basename) {
        HistoryReader ret = null;
        
        ExternalRepository rep = getRepository(parent);
        if (rep != null) {
            ret = rep.getHistoryReader(parent, basename);
        }
        
        return ret;
    }
    
    InputStream lookupHistoryGet(String parent, String basename, String rev) {
        InputStream ret = null;
        
        ExternalRepository rep = getRepository(parent);
        if (rep != null) {
            ret = rep.getHistoryGet(parent, basename, rev);
        }
        
        return ret;
    }
    
    private Map<String, ExternalRepository> externalRepositories;
}
