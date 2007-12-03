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
package org.opensolaris.opengrok.history;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 * The HistoryGuru is used to implement an transparent layer to the various
 * source control systems.
 *
 * @author Chandan
 */
public class HistoryGuru {
    /** The one and only instance of the HistoryGuru */
    private static HistoryGuru instance = new HistoryGuru();

    /** The different SourceControlSystems currently supported */
    private enum SCM { 
        /** Unknown version control system for last file */
        UNKNOWN,
        /** RCS was used by the last file */
        RCS,
        /** SCCS was used by the last file */
        SCCS,
        /** Subversion was used by the last file */
        SVN,
        /** The last file was located in an "external" repository */
        EXTERNAL };
   
    /** Method used on the last file */
    private SCM previousFile;
    /** Is JavaSVN available? */
    private boolean svnAvailable;
    /** Is JavaSVN initialized? */
    private boolean initializedSvn;
    /** The name of the Subversion subdirectory */
    private static final String svnlabel = ".svn";
    
    private boolean isSvnAvailable() {
        if (!initializedSvn) {
            initializedSvn = true;
            try {
                if (Class.forName("org.tigris.subversion.javahl.SVNClient") != null) {
                    svnAvailable = true;
                }
            } catch (ClassNotFoundException ex) {
                System.err.println("Could not find the supported Subversion bindings.");
                System.err.println("Please verify that you have svn-javahl.jar installed.");
                ex.printStackTrace();
            } catch (UnsatisfiedLinkError ex) {
                System.err.println("Failed to initialize Subversion library.");
                System.err.println("Please verify that you have Subversions native library in your ");
                if (File.separatorChar == '/') {
                    System.err.println("Please verify that you have Subversions native library (libsvnjavahl-1.so) in your LD_LIBRARY_PATH");                    
                } else {
                    System.err.println("Please verify that you have the Subversion native library (dll) in your PATH");
                }
                ex.printStackTrace();
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
        previousFile = SCM.UNKNOWN;
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
    {
        Class<? extends HistoryParser> hpClass = lookupHistoryParser(file);
        if (hpClass != null) {
            previousFile = SCM.EXTERNAL;
        } else {
            File rcsfile = RCSHistoryParser.getRCSFile(file);
            if (rcsfile != null && rcsfile.exists()) {
                hpClass = RCSHistoryParser.class;
                previousFile = SCM.RCS;
            } else {
                if (SCCSHistoryParser.getSCCSFile(file).canRead()) {
                    hpClass = SCCSHistoryParser.class;
                    previousFile = SCM.SCCS;
                } else {
                    File svn = new File(file.getParent(), svnlabel);

                    if (svn.exists() && isSvnAvailable()) {
                        hpClass = SubversionHistoryParser.class;
                        previousFile = SCM.SVN;
                    }
                }
            }
        }
        if (hpClass == null) {
            previousFile = SCM.UNKNOWN;
        }
                
        return hpClass;
    }
    
    
    /**
     * Get the <code>HistoryParser</code> to use for the specified file.
     */
    private Class<? extends HistoryParser> getHistoryParser(File file) {
        Class<? extends HistoryParser> parser = null;

        switch (previousFile) {
        case EXTERNAL:
            ExternalRepository repos = getRepository(file.getParentFile());
            if (repos != null) {
                parser = repos.getHistoryParser();
            }
            break;
        case SVN:
            if (new File(file.getParent(), svnlabel).exists()) {
                parser = SubversionHistoryParser.class;
            }
            break;
        case RCS:
            File rcsfile = RCSHistoryParser.getRCSFile(file);
            if (rcsfile != null && rcsfile.exists()) {
                parser = RCSHistoryParser.class;
            }
            break;
        case SCCS:
            if (SCCSHistoryParser.getSCCSFile(file).canRead()) {
                parser = SCCSHistoryParser.class;
            }
            break;
        }

        if (parser == null) {
            // I did not find a match for the specified system. try to guess..
            parser = guessHistoryParser(file);
        }

        return parser;
    }

    /**
     * Annotate the specified revision of a file.
     *
     * @param file the file to annotate
     * @param rev the revision to annotate (<code>null</code> means BASE)
     * @return file annotation, or <code>null</code> if the
     * <code>HistoryParser</code> does not support annotation
     */
    public Annotation annotate(File file, String rev) throws Exception {
        Class<? extends HistoryParser> parserClass = getHistoryParser(file);
        if (parserClass != null) {
            HistoryParser parser = parserClass.newInstance();
            ExternalRepository repos = getRepository(file.getParentFile());
            return parser.annotate(file, rev, repos);
        }
        return null;
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

        Class<? extends HistoryParser> parser = getHistoryParser(file);

        if (parser != null) {
            try {
                ExternalRepository repos = getRepository(file.getParentFile());
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
        ExternalRepository repos = getRepository(file);
         if (repos != null) {
             parser = repos.getHistoryParser();
         } else if ((new File(file.getParentFile(), ".svn")).exists()) {
             parser = SubversionHistoryParser.class;
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
        InputStream in = lookupHistoryGet(parent, basename, rev);
        
        if (in != null) {
            previousFile = SCM.EXTERNAL;
        } else {
            File rcsfile = RCSHistoryParser.getRCSFile(parent, basename);
            if (rcsfile != null) {
                String rcspath = rcsfile.getPath();
                in = new BufferedInputStream(new RCSget(rcspath, rev));
                previousFile = SCM.RCS;
            } else {
                File history = SCCSHistoryParser.getSCCSFile(parent, basename);
                if(history.canRead()) {
                    in = new BufferedInputStream(new SCCSget(new FileInputStream(history), rev));
                    in.mark(32);
                    in.read();
                    in.reset();
                    previousFile = SCM.RCS;
                } else {
                    File svn = new File(parent, svnlabel);
                    if (svn.exists() && isSvnAvailable()) {
                        in = new BufferedInputStream(new SubversionGet(parent, basename, rev));
                        previousFile = SCM.SVN;
                    }
                }
            }
        }
        
        if (in == null) {
            previousFile = SCM.UNKNOWN;
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
                File rcsfile = RCSHistoryParser.getRCSFile(parent, basename);
                if (rcsfile != null) {
                    String rcspath = rcsfile.getPath();
                    in = new BufferedInputStream(new RCSget(rcspath, rev));
                }
                break;
                
            case SCCS :
                history = SCCSHistoryParser.getSCCSFile(parent, basename);
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
        return
            (new File(parent, "SCCS")).isDirectory() ||
            (new File(parent, "RCS")).isDirectory() ||
            (new File(parent, "CVS")).isDirectory() ||
            (new File(parent, svnlabel)).isDirectory() ||
            (getRepository(new File(parent)) != null);
    }

    /**
     * Check if we can annotate the specified file.
     *
     * @param file the file to check
     * @return <code>true</code> if the file is under version control and the
     * version control system supports annotation
     */
    public boolean hasAnnotation(File file) {
        if (file.isDirectory()) {
            return false;
        }
        Class<? extends HistoryParser> parser = getHistoryParser(file);
        if (parser == null) {
            return false;
        }
        try {
            return parser.newInstance().supportsAnnotation();
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {
        try{
            File f = new File(args[0]);
            HistoryGuru.getInstance().addExternalRepositories(".");
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

            System.out.println("-----Annotate");
            Annotation annotation = HistoryGuru.getInstance().annotate(
                    f, (args.length > 1 ? args[1] : null));
            if (annotation == null) {
                System.out.println("<null>");
            } else {
                for (int i = 1; i <= annotation.size(); i++) {
                    System.out.println("Line " + i + "\t" +
                                       annotation.getRevision(i) + ", " +
                                       annotation.getAuthor(i));
                }
            }
        } catch (Exception e) {
            System.err.println("Exception " + e +
                               "\nUsage: HistoryGuru file [annotate-rev]");
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
                } else if (name.equals(".svn") && isSvnAvailable()) {
                    try {
                        String s = files[ii].getParentFile().getCanonicalPath();
                        System.out.println("Adding Subversion repository: <" + s + ">");
                        SubversionRepository rep = new SubversionRepository(s);                        
                        addExternalRepository(rep, s, repos);
                        return ;
                    } catch (IOException exp) {
                        System.err.println("Failed to get canonical path for " + files[ii].getName() + ": " + exp.getMessage());
                        System.err.println("Repository will be ignored...");
                        exp.printStackTrace(System.err);
                    }                    
                } else if (name.equals("cvs") || name.equals("sccs")) {
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
    
    /**
     * Search through the all of the directories and add all of the source
     * repositories found.
     * 
     * @param dir the root directory to start the search in.
     */
    public void addExternalRepositories(String dir) {
        Map<String, ExternalRepository> repos = new HashMap<String, ExternalRepository>();
        addExternalRepositories((new File(dir)).listFiles(), repos);
        RuntimeEnvironment.getInstance().setRepositories(repos);
    }

    private ExternalRepository getRepository(File path) {
        Map<String, ExternalRepository> repos = RuntimeEnvironment.getInstance().getRepositories();
        
        while (path != null) {
            try {
                ExternalRepository r = repos.get(path.getCanonicalPath());
                if (r != null) return r;
            } catch (IOException e) {
                System.err.println("Failed to get canonical path for " + path);
                e.printStackTrace();
            }
            path = path.getParentFile();
        }

        return null;
    }
    
    private Class<? extends HistoryParser> lookupHistoryParser(File file) {
        Class<? extends HistoryParser> ret = null;

        ExternalRepository rep = getRepository(file.getParentFile());
        if (rep != null) {
            ret = rep.getHistoryParser();
        }
        
        return ret;
    }
    
    private InputStream lookupHistoryGet(String parent, String basename,
                                         String rev) {
        ExternalRepository rep = getRepository(new File(parent));
        if (rep != null) {
            return rep.getHistoryGet(parent, basename, rev);
        }
        return null;
    }
    
}
