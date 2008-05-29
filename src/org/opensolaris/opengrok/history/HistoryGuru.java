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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.index.IgnoredNames;

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
    
    private String SCCSCommand = System.getProperty("org.opensolaris.opengrok.history.Teamware", "sccs");
    
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
                File sccsfile = SCCSHistoryParser.getSCCSFile(file);
                if (sccsfile != null && sccsfile.canRead()) {
                    hpClass = SCCSHistoryParser.class;
                    previousFile = SCM.SCCS;
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

        switch (previousFile) {
        case EXTERNAL:
            Repository repos = getRepository(file.getParentFile());
            if (repos != null && repos.fileHasHistory(file)) {
                Class<? extends HistoryParser> parser;
                parser = repos.getHistoryParser();
                if (parser != null) {
                    return parser;
                }
            }
            break;
        case RCS:
            File rcsfile = RCSHistoryParser.getRCSFile(file);
            if (rcsfile != null && rcsfile.exists()) {
                return RCSHistoryParser.class;
            }
            break;
        case SCCS:
            File sccsfile = SCCSHistoryParser.getSCCSFile(file);
            if (sccsfile != null && sccsfile.canRead()) {
                return SCCSHistoryParser.class;
            }
            break;
        }

        // I did not find a match for the specified system. try to guess..
        return guessHistoryParser(file);
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
        Repository repos = getRepository(file);
        if (repos != null) {
            return repos.annotate(file, rev);
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
                Repository repos = getRepository(file.getParentFile());
                History history = HistoryCache.get(file, parser, repos);
                if (history == null) {
                    return null;
                }
                return new HistoryReader(history);
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
        Repository repos = getRepository(file);
        if (repos != null) {
            parser = repos.getDirectoryHistoryParser();
        }

        if (parser == null) {
            // I did not find a match for the specified system. Use the default directory reader
            parser = DirectoryHistoryParser.class;
            repos = null;
        }

        if (parser != null) {
            try {
                History history = HistoryCache.get(file, parser, repos);
                if (history != null) {
                    return new HistoryReader(history);
                }
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
                if (history.canRead()) {
                    in = SCCSget.getRevision(SCCSCommand,history, rev);
                    in.mark(32);
                    in.read();
                    in.reset();
                    previousFile = SCM.SCCS;
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
                if (history != null && history.canRead()) {
                    in = SCCSget.getRevision(SCCSCommand,history, rev);
                    in.mark(32);
                    in.read();
                    in.reset();
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
     * @param file The name of the directory
     * @return true if the files in this directory have associated revision history
     */
    public boolean hasHistory(File file) {
        Repository repos = getRepository(file);
        if (repos != null) {
            return repos.fileHasHistory(file);
        }
        
        if (!file.isDirectory()) {
            file = file.getParentFile();
        } 
        
        return (new File(file, "SCCS")).isDirectory() ||
               (new File(file, "RCS")).isDirectory() ||
               (new File(file, "CVS")).isDirectory();
    }

    /**
     * Check if we can annotate the specified file.
     *
     * @param file the file to check
     * @return <code>true</code> if the file is under version control and the
     * version control system supports annotation
     */
    public boolean hasAnnotation(File file) {
        if (!file.isDirectory()) {
            Repository repos = getRepository(file);
            if (repos != null) {
                return repos.supportsAnnotation();
            }
        }
        
        return false;
    }

    public static void main(String[] args) {
        try{
            File f = new File(args[0]);
            File d = new File(".");
            RuntimeEnvironment.getInstance().setSourceRootFile(d.getCanonicalFile());
            HistoryGuru.getInstance().addRepositories(d.getCanonicalPath());
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
    
    private void addMercurialRepository(File file, Map<String, Repository> repos, IgnoredNames ignoredNames) {
        try {
            String s = file.getCanonicalPath();
            if (RuntimeEnvironment.getInstance().isVerbose()) {
                System.out.println("Adding Mercurial repository: <" + s + ">");
            }
            MercurialRepository rep = new MercurialRepository(s);
            addRepository(rep, s, repos);
        } catch (IOException exp) {
            System.err.println("Failed to get canonical path for " + file.getAbsolutePath() + ": " + exp.getMessage());
            System.err.println("Repository will be ignored...");
            exp.printStackTrace(System.err);
        }

        // The forest-extension in Mercurial adds repositories inside the
        // repositories. I don't want to traverse all subdirectories in the
        // repository searching for .hg-directories, but I will search all the
        // toplevel directories. 
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    File child = new File(f, ".hg");
                    if (child.exists()) {
                        addMercurialRepository(f, repos, ignoredNames);
                    }
                }
            }
        }
    }
    
    private void addRepositories(File[] files, Map<String, Repository> repos,
            IgnoredNames ignoredNames) {
        for (int ii = 0; ii < files.length; ++ii) {
            if (files[ii].isDirectory()) {
                String name = files[ii].getName().toLowerCase();
                if (name.equals(".hg")) {
                    addMercurialRepository(files[ii].getParentFile(), repos, ignoredNames);
                    return;
                } else if (name.equals(".bzr")) {
                    try {
                        String s = files[ii].getParentFile().getCanonicalPath();
                        if (RuntimeEnvironment.getInstance().isVerbose()) {
                            System.out.println("Adding Bazaar repository: <" + s + ">");
                        }
                        BazaarRepository rep = new BazaarRepository(s);
                        addRepository(rep, s, repos);
                    } catch (IOException exp) {
                        System.err.println("Failed to get canonical path for " + files[ii].getParentFile().getAbsolutePath() + ": " + exp.getMessage());
                        System.err.println("Repository will be ignored...");
                        exp.printStackTrace(System.err);
                    }
                    return;
                } else if (name.equals(".git")) {
                    try {
                        String s = files[ii].getParentFile().getCanonicalPath();
                        if (RuntimeEnvironment.getInstance().isVerbose()) {
                            System.out.println("Adding Git repository: <" + s + ">");
                        }
                        GitRepository rep = new GitRepository(s);
                        addRepository(rep, s, repos);
                    } catch (IOException exp) {
                        System.err.println("Failed to get canonical path for " + files[ii].getParentFile().getAbsolutePath() + ": " + exp.getMessage());
                        System.err.println("Repository will be ignored...");
                        exp.printStackTrace(System.err);
                    }
                    return;
                } else if (name.equals(svnlabel)) {
                    if (isSvnAvailable()) {
                        try {
                            String s = files[ii].getParentFile().getCanonicalPath();
                            if (RuntimeEnvironment.getInstance().isVerbose()) {
                                System.out.println("Adding Subversion repository: <" + s + ">");
                            }
                            SubversionRepository rep = new SubversionRepository(s);                        
                            addRepository(rep, s, repos);
                        } catch (IOException exp) {
                           System.err.println("Failed to get canonical path for " + files[ii].getName() + ": " + exp.getMessage());
                           System.err.println("Repository will be ignored...");
                           exp.printStackTrace(System.err);
                        }
                    }
                    return;
                } else if (name.equals("codemgr_wsdata")) {
                    try {
                        String s = files[ii].getParentFile().getCanonicalPath();
                        System.out.println("Adding Teamware repository: <" + s + ">");
                        TeamwareRepository rep = new TeamwareRepository(s);
                        addRepository(rep, s, repos);
                    } catch (IOException exp) {
                        System.err.println("Failed to get canonical path for " + files[ii].getName() + ": " + exp.getMessage());
                        System.err.println("Repository will be ignored...");
                        exp.printStackTrace(System.err);
                    }
                    return;
                } else if (name.equals("cvs") || name.equals("sccs")) {
                    return;
                } else if (new File(files[ii].getParentFile(), "view.dat").exists() ||
                           files[ii].getParentFile().getName().toLowerCase().equals("vobs")) {
                          // if the parent contains a file named "view.dat" or
                          // the parent is named "vobs"
                    try {
                        String s = files[ii].getParentFile().getCanonicalPath();
                        System.out.println("Adding ClearCase repository: <" + s + ">");
                        ClearCaseRepository rep = new ClearCaseRepository(s);
                        addRepository(rep, s, repos);
                        return ;
                    } catch (IOException exp) {
                        System.err.println("Failed to get canonical path for " + files[ii].getName() + ": " + exp.getMessage());
                        System.err.println("Repository will be ignored...");
                        exp.printStackTrace(System.err);
                    }
                } else if (PerforceRepository.isInP4Depot(files[ii])) {                    
                    try {
                        String s = files[ii].getParentFile().getCanonicalPath();
                        System.out.println("Adding Perforce repository: <" + s + ">");
                        PerforceRepository rep = new PerforceRepository();
                        addRepository(rep, files[ii].getParentFile().getCanonicalPath(), repos);
                    } catch (IOException exp) {
                        System.err.println("Failed to get canonical path for " + files[ii].getName() + ": " + exp.getMessage());
                        System.err.println("Repository will be ignored...");
                        exp.printStackTrace(System.err);
                    }
                }
            }
        }
        
        // Nope, search it's sub-dirs
        for (int ii = 0; ii < files.length; ++ii) {
            if (files[ii].isDirectory() &&
                    !ignoredNames.ignore(files[ii])) {
                // Could return null if e.g. the directory is unreadable
                File[] dirfiles = files[ii].listFiles();
                if (dirfiles != null) {
                    addRepositories(files[ii].listFiles(), repos, ignoredNames);
                } else {
                    try {
                        String s = files[ii].getCanonicalPath();
                        System.err.println("Failed to read directory: " + s);
                    } catch (IOException exp) {
                        System.err.println("Failed to read directory (could not get canonical path): "
                                + files[ii].getName() + ": " + exp.getMessage());
                        exp.printStackTrace(System.err);
                    }                    
                }
            }
        }
    }
    
    private void addRepository(Repository rep, String path, Map<String, Repository> repos) {
        repos.put(path, rep);
    }
    
    /**
     * Search through the all of the directories and add all of the source
     * repositories found.
     * 
     * @param dir the root directory to start the search in.
     */
    public void addRepositories(String dir) {
        Map<String, Repository> repos = new HashMap<String, Repository>();
        addRepositories((new File(dir)).listFiles(), repos,
                RuntimeEnvironment.getInstance().getIgnoredNames());
        RuntimeEnvironment.getInstance().setRepositories(repos);
    }

    /**
     * Update the source the contents in the source repositories.
     */
    public void updateRepositories() {
        boolean verbose = RuntimeEnvironment.getInstance().isVerbose();
        
        for (Map.Entry<String, Repository> entry : RuntimeEnvironment.getInstance().getRepositories().entrySet()) {
            Repository repository = entry.getValue();
            
            String path = entry.getKey();
            String type = repository.getClass().getSimpleName();
            
            if (verbose) {
                System.out.print("Update " + type + " repository in " + path);
                System.out.flush();
            }
            
            try {
                repository.update();
            } catch (Exception e) {
                System.err.println("An error occured while updating " + path + " (" + type + ")");
                e.printStackTrace();
            }

            if (verbose) {
                System.out.println();
            }
        }
    }
    
    private void createCache(Repository repository) {
        boolean verbose = RuntimeEnvironment.getInstance().isVerbose();
        String path = repository.getDirectoryName();
        String type = repository.getClass().getSimpleName();
        long start = System.currentTimeMillis();
        
        if (verbose) {
            System.out.print("Create historycache for " + path + " (" + type + ")");
            System.out.flush();
        }

        try {
            repository.createCache();
        } catch (Exception e) {
            System.err.println("An error occured while creating cache for " + path + " (" + type + ")");
            e.printStackTrace();
        }
        long stop = System.currentTimeMillis();
        if (verbose) {
            System.out.println(" (" + (stop - start) + "ms)");
        }   
    }
    
    /**
     * Create the history cache for all of the repositories
     */
    public void createCache() {
        boolean threading = System.getProperty("org.opensolaris.opengrok.history.threads", null) != null;
        ArrayList<Thread> threads = new ArrayList<Thread>();
        
        for (Map.Entry<String, Repository> entry : RuntimeEnvironment.getInstance().getRepositories().entrySet()) {
            if (threading) {
                final Repository repos = entry.getValue();
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        createCache(repos);
                    }                    
                });
                t.start();
                threads.add(t);
            } else {
                createCache(entry.getValue());
            }
        }
        // Wait for all threads to finish
        while (threads.size() > 0) {
            for (Thread t : threads) {
                if (!t.isAlive()) {
                    try {
                        t.join();
                        threads.remove(t);
                        break;
                    } catch (InterruptedException ex) {
                    }
                } 
            }
            try {
                Thread.sleep(1000);
            } catch (Exception e) {}
        }
    }

    public void createCache(List<String> repositories) {
        boolean threading = System.getProperty("org.opensolaris.opengrok.history.threads", null) != null;
        ArrayList<Thread> threads = new ArrayList<Thread>();
        File root = RuntimeEnvironment.getInstance().getSourceRootFile();
        for (String file : repositories) {
            final Repository repos = getRepository(new File(root, file));
            if (repos != null) {
                if (threading) {
                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            createCache(repos);
                        }
                    });
                    t.start();
                    threads.add(t);
                } else {
                    createCache(repos);
                }
            }
        }
        
        // Wait for all threads to finish
        while (threads.size() > 0) {
            for (Thread t : threads) {
                if (!t.isAlive()) {
                    try {
                        t.join();
                        threads.remove(t);
                        break;
                    } catch (InterruptedException ex) {
                    }
                } 
            }    
            try {
                Thread.sleep(1000);
            } catch (Exception e) {}
        }
    }

    
    private Repository getRepository(File path) {
        Map<String, Repository> repos = RuntimeEnvironment.getInstance().getRepositories();
        
        try {
            path = path.getCanonicalFile();
        } catch (IOException e) {
            System.err.println("Failed to get canonical path for " + path);
            e.printStackTrace();
            return null;
        }
        while (path != null) {
            try {
                Repository r = repos.get(path.getCanonicalPath());
                if (r != null) {
                    return r;
                }
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

        Repository rep = getRepository(file.getParentFile());
        if (rep != null) {
            ret = rep.getHistoryParser();
        }
        
        return ret;
    }
    
    private InputStream lookupHistoryGet(String parent, String basename,
                                         String rev) {
        Repository rep = getRepository(new File(parent));
        if (rep != null) {
            return rep.getHistoryGet(parent, basename, rev);
        }
        return null;
    }
   
}
