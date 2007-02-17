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
 * ident        "@(#)HistoryGuru.java 1.2     06/02/22 SMI"
 */
package org.opensolaris.opengrok.history;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
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
    }
    
    /**
     * Get the one and only instance of the HistoryGuru
     * @return the one and only HistoryGuru instance
     */
    public static HistoryGuru getInstance()  {
        return instance;
    }
    
    /**
     * Try to guess the correct history parser to use. Create an RCS, SCCS or Subversion
     * HistoryParser if it looks like the file is managed by the appropriate
     * revision control system.
     *
     * @param file The the file to get the history parser for
     * @throws java.io.IOException If an error occurs while trying to access the filesystem
     * @return A subclass of HistorParser that may be used to read out history
     * data for a named file
     */
    private Class<? extends HistoryParser> guessHistoryParser(File file)
        throws IOException
    {
        Class<? extends HistoryParser> hpClass = null;
        File rcsfile = Util.getRCSFile(file);
        if (rcsfile != null && rcsfile.exists()) {
            hpClass = RCSHistoryParser.class;
            previousFile = RCS;
        } else {
            if (Util.getSCCSFile(file).canRead()) {
                hpClass = SCCSHistoryParser.class;
                previousFile = SCCS;
            } else {
                File svn = new File(file.getParent(), svnlabel);
                
                if (svn.exists() && isSvnAvailable()) {
                    try {
                        hpClass = SubversionHistoryParser.class;
                        previousFile = SVN;
                    } catch (Exception e) {
                        ;
                    }
                } else {
                    hpClass = lookupHistoryParser(file);
                    if (hpClass != null) {
                        previousFile = EXTERNAL;
                    }
                }
            }
        }
        
        if (hpClass == null) {
            previousFile = UNKNOWN;
        }
        return hpClass;
    }
    
    
    /**
     * Get the appropriate history reader for the file specified by parent and basename.
     * If configured, it will try to use the configured system. If the file is under another
     * revision control system, it will try to guess the correct system.
     *
     * @param file The file to get the history reader for
     * @throws java.io.IOException If an error occurs while trying to access the filesystem
     * @return A HistorReader that may be used to read out history data for a named file
     */
    public HistoryReader getHistoryReader(File file) throws IOException {
        if (file.isDirectory()) {
            return getDirectoryHistoryReader(file);
        }
        
        Class<? extends HistoryParser> parser = null;
        ExternalRepository repos = null;
        
        switch (previousFile) {
            case EXTERNAL :
                repos = getRepository(file.getParent());
                if (repos != null) {
                    parser = repos.getHistoryParser();
                }
                break;
                
            case SVN :
                if (new File(file.getParent(), svnlabel).exists()) {
                    parser = SubversionHistoryParser.class;
                }
                break;
                
            case RCS :
                File rcsfile = Util.getRCSFile(file);
                if (rcsfile != null && rcsfile.exists()) {
                    parser = RCSHistoryParser.class;
                }
                break;
                
            case SCCS :
                if (Util.getSCCSFile(file).canRead()) {
                    parser = SCCSHistoryParser.class;
                }
                break;
                
            default:
                ;
        }
        
        if (parser == null) {
            // I did not find a match for the specified system. try to guess..
            parser = guessHistoryParser(file);
            if (previousFile == EXTERNAL) {
                repos = getRepository(file.getParent());
            } else {
                repos = null;
            }
        }

        if (parser != null) {
            try {
                return new HistoryReader(HistoryCache.get(file, parser, repos));
            } catch (IOException ioe) {
                throw ioe;
            } catch (Exception e) {
                IOException ioe =
                    new IOException("Error while constructing HistoryReader");
                ioe.initCause(e);
                throw ioe;
            }
        }

        return null;
    }
    
    private HistoryReader getDirectoryHistoryReader(File file) throws IOException {
        Class<? extends HistoryParser> parser = null;
        ExternalRepository repos = getRepository(file.getAbsolutePath());
         if (repos != null) {
             parser = repos.getHistoryParser();
         }

        if (parser == null) {
            // I did not find a match for the specified system. Use the default directory reader
            parser = DirectoryHistoryParser.class;
            repos = null;
        }

        if (parser != null) {
            try {
                return new HistoryReader(HistoryCache.get(file, parser, repos));
            } catch (IOException ioe) {
                throw ioe;
            } catch (Exception e) {
                e.printStackTrace();
                IOException ioe =
                    new IOException("Error while constructing HistoryReader");
                ioe.initCause(e);
                throw ioe;
            }
        }

        return null;
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
    private InputStream guessGetRevision(String parent, String basename, String rev) throws IOException {
        InputStream in = null;
        File rcsfile = Util.getRCSFile(parent, basename);
        if (rcsfile != null) {
            String rcspath = rcsfile.getPath();
            in = new BufferedInputStream(new RCSget(rcspath, rev));
            previousFile = RCS;
        } else {
            File history = Util.getSCCSFile(parent, basename);
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
        File history;
        
        switch (previousFile) {
            case RCS :
                File rcsfile = Util.getRCSFile(parent, basename);
                if (rcsfile != null) {
                    String rcspath = rcsfile.getPath();
                    in = new BufferedInputStream(new RCSget(rcspath, rev));
                }
                break;
                
            case SCCS :
                history = Util.getSCCSFile(parent, basename);
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
                (new File(parent + "/RCS")).isDirectory() ||
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
            
            System.out.println("-----Reading comments as a reader");
            HistoryReader hr = HistoryGuru.getInstance().getHistoryReader(f);
            BufferedReader rr = new BufferedReader(hr);
            int c;
            BufferedOutputStream br = new BufferedOutputStream(System.out);
            while((c = rr.read()) != -1) {
                br.write((char)c);
            }
            br.flush();
            
            System.out.println("-----Reading comments as lines");
            hr = HistoryGuru.getInstance().getHistoryReader(f);
            while(hr.next()) {
                System.out.println(hr.getLine() + "----------------------");
            }
            hr.close();
            
            System.out.println("-----Reading comments structure");
            hr = HistoryGuru.getInstance().getHistoryReader(f);
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
    
    private void addExternalRepositories(File[] files, Map<String, ExternalRepository> repos) {
        // Check if this directory contain a file named .hg
        for (int ii = 0; ii < files.length; ++ii) {
            if (files[ii].isDirectory()) {
                String name = files[ii].getName().toLowerCase();
                if (name.equals(".hg")) {
                    try {
                        String s = files[ii].getParentFile().getCanonicalPath();
                        System.out.println("Adding Mercurial repository: <" + s + ">");
                        MercurialRepository rep = new MercurialRepository(s);                        
                        addExternalRepository(rep, s, repos);
                        return ;
                    } catch (IOException exp) {
                        System.err.println("Failed to get canonical path for " + files[ii].getName() + ": " + exp.getMessage());
                        System.err.println("Repository will be ignored...");
                        exp.printStackTrace(System.err);
                    }
                } else if (name.equals(".svn") || name.equals("cvs") || name.equals("sccs")) {
                    return;
                }
            }
        }
        
        // Nope, search it's sub-dirs
        for (int ii = 0; ii < files.length; ++ii) {
            if (files[ii].isDirectory()) {
                addExternalRepositories(files[ii].listFiles(), repos);
            }
        }
    }
    
    private void addExternalRepository(ExternalRepository rep, String path, Map<String, ExternalRepository> repos) {
        repos.put(path, rep);
    }
    
    public void addExternalRepositories(String dir) {
        Map<String, ExternalRepository> repos = new HashMap<String, ExternalRepository>();
        addExternalRepositories((new File(dir)).listFiles(), repos);
        RuntimeEnvironment.getInstance().setRepositories(repos);
    }

    private ExternalRepository getRepository(String path) {
        ExternalRepository ret = null;
        
        Map<String, ExternalRepository> repos = RuntimeEnvironment.getInstance().getRepositories();
        
        while (path != null) {
            ExternalRepository r = repos.get(path);
            if (r != null) {
                ret = r;
                break;
            }
            path = (new File(path)).getParent();
        }
        return ret;
    }
    
    private Class<? extends HistoryParser> lookupHistoryParser(File file) {
        Class<? extends HistoryParser> ret = null;

        ExternalRepository rep = getRepository(file.getParent());
        if (rep != null) {
            ret = rep.getHistoryParser();
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
    
}
