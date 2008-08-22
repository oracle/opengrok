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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.util.Getopt;

/**
 * Creates and updates an inverted source index
 * as well as generates Xref, file stats etc., if specified
 * in the options
 */
@SuppressWarnings({"PMD.AvoidPrintStackTrace","PMD.SystemPrintln"})
public final class Indexer {
   
   private static Indexer index = new Indexer();
   private static final Logger log = Logger.getLogger(Indexer.class.getName());
   
   public static Indexer getInstance() {
      return index;
   }
    /**
     * Program entry point
     * @param argv argument vector
     */
    public static void main(String argv[]) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        boolean runIndex = true;
        boolean update = true;
        boolean optimizedChanged = false;
        CommandLineOptions cmdOptions = new CommandLineOptions();
        
        if (argv.length == 0) {
            System.err.println(cmdOptions.getUsage());
            System.exit(1);
        } else {
            boolean searchRepositories = false;
            ArrayList<String> subFiles = new ArrayList<String>();
            ArrayList<String> repositories = new ArrayList<String>();
            String configFilename = null;
            String configHost = null;
            boolean addProjects = false;
            boolean refreshHistory = false;
            String defaultProject = null;
            boolean listFiles = false;
            boolean createDict = false;
            int noThreads = Runtime.getRuntime().availableProcessors();
            
            // Parse command line options:
            Getopt getopt = new Getopt(argv, cmdOptions.getCommandString());

            try {
                getopt.parse();
            } catch (ParseException ex) {
                System.err.println("OpenGrok: " + ex.getMessage());
                System.err.println(cmdOptions.getUsage());
                System.exit(1);
            }

            try{
                int cmd;
                
                // We need to read the configuration file first, since we
                // will try to overwrite options..
                while ((cmd = getopt.getOpt()) != -1) {
                    if (cmd == 'R') {
                        env.readConfiguration(new File(getopt.getOptarg()));
                        break;
                    }
                }
                
                // Now we can handle all the other options..
                getopt.reset();                
                while ((cmd = getopt.getOpt()) != -1) {
                    switch (cmd) {
                    case 't':
                        createDict = true;
                        runIndex = false;
                        break;

                    case 'q': env.setVerbose(false); break;
                    case 'e': env.setGenerateHtml(false); break;
                    case 'P': addProjects = true; break;
                    case 'p': defaultProject = getopt.getOptarg(); break;
                    case 'c': env.setCtags(getopt.getOptarg()); break;
                    case 'w': {
                        String webapp = getopt.getOptarg();
                        if (!webapp.startsWith("/") && !webapp.startsWith("http")) {
                            webapp = "/" + webapp;
                        }
                        if (webapp.endsWith("/")) {
                            env.setUrlPrefix(webapp + "s?");
                        } else {
                            env.setUrlPrefix(webapp + "/s?");
                        }
                    }
                    break;
                    case 'W': configFilename = getopt.getOptarg(); break;
                    case 'U': configHost = getopt.getOptarg(); break;
                    case 'R': 
                        // already handled
                        break;
                    case 'n': runIndex = false; break;
                    case 'H': refreshHistory = true; break;
                    case 'h' : repositories.add(getopt.getOptarg()); break;
                    case 'r': {
                        if (getopt.getOptarg().equalsIgnoreCase("on")) {
                            env.setRemoteScmSupported(true);
                        } else if (getopt.getOptarg().equalsIgnoreCase("off")) {
                            env.setRemoteScmSupported(false);
                        } else {
                            System.err.println("ERROR: You should pass either \"on\" or \"off\" as argument to -r");
                            System.err.println("       Ex: \"-r on\" will allow retrival for remote SCM systems");
                            System.err.println("           \"-r off\" will ignore SCM for remote systems");
                        }
                    }
                    break;
                    case 'O': {
                       boolean oldval = env.isOptimizeDatabase();
                        if (getopt.getOptarg().equalsIgnoreCase("on")) {
                            env.setOptimizeDatabase(true);
                        } else if (getopt.getOptarg().equalsIgnoreCase("off")) {
                            env.setOptimizeDatabase(false);
                        } else {
                            System.err.println("ERROR: You should pass either \"on\" or \"off\" as argument to -O");
                            System.err.println("       Ex: \"-O on\" will optimize the database as part of the index generation");
                            System.err.println("           \"-O off\" disable optimization of the index database");
                        }
                       if (oldval != env.isOptimizeDatabase()) {
                           optimizedChanged = true;
                        }
                    }
                    break;
                    case 'v': env.setVerbose(true); break;

                    case 's': {
                        File file = new File(getopt.getOptarg());
                        if (!file.isDirectory()) {
                            System.err.println("ERROR: No such directory: " + file.toString());
                            System.exit(1);
                        }

                        env.setSourceRootFile(file);
                        break;
                    }
                    case 'd': 
                        env.setDataRoot(getopt.getOptarg());
                        break;
                    case 'i':  
                        env.getIgnoredNames().add(getopt.getOptarg()); 
                        break;
                    case 'S' : searchRepositories = true; break;
                    case 'Q' : 
                        if (getopt.getOptarg().equalsIgnoreCase("on")) {
                            env.setQuickContextScan(true);
                        } else if (getopt.getOptarg().equalsIgnoreCase("off")) {
                            env.setQuickContextScan(false);
                        } else {
                            System.err.println("ERROR: You should pass either \"on\" or \"off\" as argument to -Q");
                            System.err.println("       Ex: \"-Q on\" will just scan a \"chunk\" of the file and insert \"[..all..]\"");
                            System.err.println("           \"-Q off\" will try to build a more accurate list by reading the complete file.");
                        }
                        
                        break;
                    case 'm' : {
                        try {
                            env.setIndexWordLimit(Integer.parseInt(getopt.getOptarg()));
                        } catch (NumberFormatException exp) {
                            System.err.println("ERROR: Failed to parse argument to \"-m\": " + exp.getMessage());
                            System.exit(1);
                        }
                        break;
                    }
                    case 'a' : 
                        if (getopt.getOptarg().equalsIgnoreCase("on")) {
                            env.setAllowLeadingWildcard(true);
                        } else if (getopt.getOptarg().equalsIgnoreCase("off")) {
                            env.setAllowLeadingWildcard(false);
                        } else {
                            System.err.println("ERROR: You should pass either \"on\" or \"off\" as argument to -a");
                            System.err.println("       Ex: \"-a on\" will allow a search to start with a wildcard");
                            System.err.println("           \"-a off\" will disallow a search to start with a wildcard");
                            System.exit(1);
                        }
                        
                        break;

                    case 'A': {
                            String[] arg = getopt.getOptarg().split(":");
                            if (arg.length != 2) {
                                System.err.println("ERROR: You must specify: -A extension:class");
                                System.err.println("       Ex: -A foo:org.opensolaris.opengrok.analysis.c.CAnalyzer");
                                System.err.println("           will use the C analyzer for all files ending with .foo");
                                System.err.println("       Ex: -A c:-");
                                System.err.println("           will disable the c-analyzer for for all files ending with .c");
                                System.exit(1);
                            }

                            arg[0] = arg[0].substring(arg[0].lastIndexOf('.') + 1).toUpperCase();
                            if (arg[1].equals("-")) {
                                AnalyzerGuru.addExtension(arg[0], null);
                                break;
                            } 

                            try {
                                AnalyzerGuru.addExtension(
                                        arg[0],
                                        AnalyzerGuru.findFactory(arg[1]));
                            } catch (Exception e) {
                                System.err.println("Unable to use " + arg[1] +
                                                   " as a FileAnalyzerFactory");
                                e.printStackTrace();
                                System.exit(1);
                            }
                        }
                        break;
                    case 'L' :
                        env.setWebappLAF(getopt.getOptarg());
                        break;
                    case 'T' :
                        try {
                            noThreads = Integer.parseInt(getopt.getOptarg());
                        } catch (NumberFormatException exp) {
                            System.err.println("ERROR: Failed to parse argument to \"-T\": " + exp.getMessage());
                            System.exit(1);
                        }
                        break;
                    case 'l' : 
                        if (getopt.getOptarg().equalsIgnoreCase("on")) {
                            env.setUsingLuceneLocking(true);
                        } else if (getopt.getOptarg().equalsIgnoreCase("off")) {
                            env.setUsingLuceneLocking(false);
                        } else {
                            System.err.println("ERROR: You should pass either \"on\" or \"off\" as argument to -l");
                            System.err.println("       Ex: \"-l on\" will enable locks in Lucene");
                            System.err.println("           \"-l off\" will disable locks in Lucene");
                        }  
                        break;
                    case '?':
                        System.err.println(cmdOptions.getUsage());
                        System.exit(0);
                        break;
                        
                    default: 
                        System.err.println("Internal Error - Unimplemented cmdline option: " + (char)cmd);
                        System.exit(1);
                    }
                }

                int optind = getopt.getOptind();
                if (optind != -1) {                
                    while (optind < argv.length) {
                        subFiles.add(argv[optind]);
                        ++optind;
                    }
                }
                
                getInstance().prepareIndexer(env, searchRepositories, addProjects,
                    defaultProject,configFilename,refreshHistory,
                    listFiles,createDict,subFiles,repositories);
            if (runIndex || (optimizedChanged && env.isOptimizeDatabase())) {
                IndexChangedListener progress = new DefaultIndexChangedListener();
                getInstance().doIndexerExecution(update, noThreads, subFiles,
                        progress);
            }
            getInstance().sendToConfigHost(env, configHost);
         } catch (IndexerException ex) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, "Exception running indexer", ex);
            System.err.println(cmdOptions.getUsage());
            System.exit(1);
         } catch (IOException ioe) {
            System.err.println("Got IOException " + ioe);
            OpenGrokLogger.getLogger().log(Level.SEVERE, "Exception running indexer", ioe);
            System.exit(1);
         }
      }

   }
                
                
    
   public void prepareIndexer(RuntimeEnvironment env,
           boolean searchRepositories,
           boolean addProjects,
           String defaultProject,
           String configFilename,
           boolean refreshHistory,
           boolean listFiles,
           boolean createDict,
           ArrayList<String> subFiles,
           ArrayList<String> repositories) throws IndexerException,IOException {
                
                if (env.getDataRootPath()  == null) {
                    throw new IndexerException("ERROR: Please specify a DATA ROOT path");
                }

                if (env.getSourceRootFile() == null) {
                    File srcConfig = new File(env.getDataRootPath(), "SRC_ROOT");
                    String line = null;
                    if(srcConfig.exists()) {
                        try {
                            BufferedReader sr = new BufferedReader(new FileReader(srcConfig));
                            line = sr.readLine();
                            sr.close();
                        } catch (IOException e) {
                        }
                    }
                    if(line == null) {
                        throw new IndexerException("ERROR: please specify a SRC_ROOT with option -s !");
                    }
                    env.setSourceRoot(line);

                    if (!env.getSourceRootFile().isDirectory()) {
                        throw new IndexerException("ERROR: No such directory:" + line);
                    }
                }

                if (!env.validateExuberantCtags()) {
                    throw new IndexerException("Didn't find Exuberant Ctags");
                }

                if (searchRepositories) {
                    if (env.isVerbose()) {
                        System.out.println("Scanning for repositories...");
                    }
                    env.getRepositories().clear();
                    long start = System.currentTimeMillis();
                    HistoryGuru.getInstance().addRepositories(env.getSourceRootPath());
                    long time = (System.currentTimeMillis() - start) / 1000;
                    if (env.isVerbose()) {
                        System.out.println("Done searching for repositories (" + time + "s)");
                    }
                }

                if (addProjects) {
                    File files[] = env.getSourceRootFile().listFiles();
                    List<Project> projects = env.getProjects();
                    projects.clear();
                    for (File file : files) {
                        if (!file.getName().startsWith(".") && file.isDirectory()) {
                            projects.add(new Project(file.getName(), "/" + file.getName()));
                        }
                    }

                    // The projects should be sorted...
                    Collections.sort(projects, new Comparator<Project>() {
                        public int compare(Project p1, Project p2){
                            String s1 = p1.getDescription();
                            String s2 = p2.getDescription();

                            int ret;
                            if (s1 == null) {
                                ret = (s2 == null) ? 0 : 1;
                            } else {
                                ret = s1.compareTo(s2);
                            }
                            return ret;
                        }
                    });
                }

                if (defaultProject != null) {
                    for (Project p : env.getProjects()) {
                        if (p.getPath().equals(defaultProject)) {
                            env.setDefaultProject(p);
                            break;
                        }
                    }
                }
                
                if (configFilename != null) {
                    if (env.isVerbose()) {
                        System.out.println("Writing configuration to " + configFilename);
                        System.out.flush();
                    }
                    env.writeConfiguration(new File(configFilename));
                    if (env.isVerbose()) {
                        System.out.println("Done...");
                        System.out.flush();
                    }
                }
                
                if (refreshHistory) {
                    HistoryGuru.getInstance().createCache();
                } else if (repositories != null && !repositories.isEmpty()) {
                    HistoryGuru.getInstance().createCache(repositories);
                }

                if (listFiles) {
                    IndexDatabase.listAllFiles(subFiles); 
                }

                if (createDict) {
                    IndexDatabase.listFrequentTokens(subFiles);
                }
   }
                
                
   public void doIndexerExecution(final boolean update, int noThreads, List<String> subFiles,
           IndexChangedListener progress) 
      throws IOException {
      RuntimeEnvironment env = RuntimeEnvironment.getInstance();
      env.register();
      log.info("Starting indexExecution");
                
                ExecutorService executor = Executors.newFixedThreadPool(noThreads);
 
                if (subFiles == null || subFiles.isEmpty()) {
                    if (update) {
                        IndexDatabase.updateAll(executor, progress);
                    } else if (env.isOptimizeDatabase()) {
                        IndexDatabase.optimizeAll(executor);
                    }
                } else {
                    List<IndexDatabase> dbs = new ArrayList<IndexDatabase>();
                    
                    for (String path : subFiles) {
                        Project project = Project.getProject(path);
                        if (project == null && env.hasProjects()) {
                            System.err.println("Warning: Could not find a project for \"" + path + "\"");
                        } else {
                            IndexDatabase db;
                            if (project != null) {
                                db = new IndexDatabase(project);
                            } else {
                                db = new IndexDatabase();                                
                            }
                            int idx = dbs.indexOf(db);
                            if (idx != -1) {
                                db = dbs.get(idx);
                            }
                            
                            if (db.addDirectory(path)) {
                                if (idx == -1) {
                                    dbs.add(db);
                                }
                            } else {
                                System.err.println("Warning: Directory does not exist \"" + path + "\"");
                            }
                        }
                    }
                    
                    for (final IndexDatabase db : dbs) {
                            final boolean optimize = env.isOptimizeDatabase();
                            db.addIndexChangedListener(progress);
                            executor.submit(new Runnable() {

                                public void run() {
                                    try {
                                        if (update) {
                                            db.update();
                                        } else if (optimize) {
                                            db.optimize();
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            });                        
                    }                    
                }
                
                executor.shutdown();
                while (!executor.isTerminated()) {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {

                    }
                }
   }

               
   public void sendToConfigHost(RuntimeEnvironment env, String configHost) {
                if (configHost != null) {
                    String[] cfg = configHost.split(":");
                    if (env.isVerbose()) {
                        log.info("Send configuration to: " + configHost);
                    }

                    if (cfg.length == 2) {
                        try {
                            InetAddress host = InetAddress.getByName(cfg[0]);
                            RuntimeEnvironment.getInstance().writeConfiguration(host, Integer.parseInt(cfg[1]));
                        } catch (Exception ex) {
                            log.log(Level.SEVERE,"Failed to send configuration to " 
                                    + configHost,ex);
                        }
                    } else {
                        System.err.println("Syntax error: ");
                        for (String s : cfg) {
                            System.err.print("[" + s + "]");
                        }
                        System.err.println();
                    }
                    if (env.isVerbose()) {
                        log.info("Configuration successfully updated");
                    }
                }       
    }

    private Indexer() {
    }
}
