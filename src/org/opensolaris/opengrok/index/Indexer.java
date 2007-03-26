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
 * ident	"%Z%%M% %I%     %E% SMI"
 */

package org.opensolaris.opengrok.index;

import java.awt.GraphicsEnvironment;
import java.io.*;
import java.net.InetAddress;
import java.util.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.oro.io.GlobFilenameFilter;
import org.opensolaris.opengrok.analysis.*;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.ExternalRepository;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.search.scope.MainFrame;

/**
 * Creates and updates an inverted source index
 * as well as generates Xref, file stats etc., if specified
 * in the options
 */

public class Indexer {
    private static boolean verbose = true;
    private static String usage = "Usage: " +
            "opengrok.jar [-qe] [-c ctagsToUse] [-H] [-R filename] [-W filename] [-U hostname:port] [-P] [-w webapproot] [-i ignore_name [ -i ..]] [-n] [-s SRC_ROOT] DATA_ROOT [subtree .. ]\n" +
            "       opengrok.jar [-O | -l | -t] DATA_ROOT\n" +
            "\t-q run quietly\n" +
            "\t-e economical - consumes less disk space\n" +
            "\t-c path to ctags\n" +
            "\t-R Read configuration from file\n" +
            "\t-W Write the current running configuration\n" +
            "\t-U Send configuration to hostname:port\n" +
            "\t-P Generate a project for each toplevel directory\n" +
            "\t-n Do not generate indexes\n" +
            "\t-H Start a threadpool to read history history\n" +
            "\t-w root URL of the webapp, default is /source\n" +
            "\t-i ignore named files or directories\n" +
            "\t-S Search and add \"External\" repositories (Mercurial...)\n" +
            "\t-s SRC_ROOT is root directory of source tree\n" +
            "\t   default: last used SRC_ROOT\n" +
            "\tDATA_ROOT - is where output of indexer is stored\n" +
            "\tsubtree - only specified files or directories under SRC_ROOT are processed\n" +
            "\t   if not specified all files under SRC_ROOT are processed\n" +
            "\n\t-O optimize the index \n" +
            "\t-l list all files in the index \n" +
            "\t-t lists tokens occuring more than 5 times. Useful for building a unix dictionary\n" +
            "\n Eg. java -jar opengrok.jar -s /usr/include /var/tmp/opengrok_data rpc";
    
    /**
     * Program entry point
     * @param argv argument vector
     */
    public static void main(String argv[]) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        boolean runIndex = true;
        
        if(argv.length == 0) {
            if (GraphicsEnvironment.isHeadless()) {
                System.err.println("No display available for the Graphical User Interface");
                System.err.println(usage);
                System.exit(1);
            } else {
                MainFrame.main(argv);
            }
            //Run Scope GUI here I am running Indexing GUI for testing
            //new IndexerWizard(null).setVisible(true);
        } else {
            boolean searchRepositories = false;
            ArrayList<String> subFiles = new ArrayList<String>();
            String configFilename = null;
            String configHost = null;
            boolean addProjects = false;
            boolean refreshHistory = false;
            
            try{
                if (argv == null || argv.length < 2) {
                    System.err.println(usage);
                    System.exit(1);
                }
                for (int i = 0; i < argv.length ; i++) {
                    if (argv[i].equals("-O")) {
                        if (argv.length == 2 && i+1 < argv.length) {
                            Index.doOptimize(new File(argv[i+1]));
                            System.exit(0);
                        } else {
                            System.err.println("ERROR: Invalid option or No data root specified!");
                            System.err.println(usage);
                            System.exit(1);
                        }
                    } else if (argv[i].equals("-g")) {
                        if(i+1 < argv.length) {
                            IgnoredNames.glob = new GlobFilenameFilter(argv[++i]);
                        }
                    } else if (argv[i].equals("-q")) {
                        verbose = false;
                    } else if (argv[i].equals("-e")) {
                        env.setGenerateHtml(true);
                    } else if (argv[i].equals("-P")) {
                        addProjects = true;
                    } else if (argv[i].equals("-c")) {
                        if(i+1 < argv.length) {
                            env.setCtags(argv[++i]);
                        }
                    } else if (argv[i].equals("-w")) {
                        if(i+1 < argv.length) {
                            String webapp = argv[++i];
                            if(webapp.startsWith("/") || webapp.startsWith("http")) {
                                ;
                            } else {
                                webapp = "/" + webapp;
                            }
                            if(webapp.endsWith("/")) {
                                env.setUrlPrefix(webapp + "s?");
                            } else {
                                env.setUrlPrefix(webapp + "/s?");
                            }
                        }
                    } else if (argv[i].equals("-W")) {
                        if(i+1 < argv.length) {
                            configFilename = argv[++i];
                        }
                    } else if (argv[i].equals("-U")) {
                        if(i+1 < argv.length) {
                            configHost = argv[++i];
                        }
                    } else if (argv[i].equals("-R")) {
                        if(i+1 < argv.length) {
                            env.readConfiguration(new File(argv[++i]));
                        }
                    } else if (argv[i].equals("-n")) {
                        runIndex = false;
                    } else if (argv[i].equals("-H")) {
                        refreshHistory = true;
                    } else if (argv[i].equals("-l")) {
                        if (argv.length == 2 && i+1 < argv.length) {
                            Index.doList(new File(argv[i+1]));
                            System.exit(0);
                        } else {
                            System.err.println("ERROR: Invalid option or No data root specified!");
                            System.err.println(usage);
                            System.exit(1);
                        }
                    } else if (argv[i].equals("-t")) {
                        if (argv.length == 2 && i+1 < argv.length) {
                            Index.doDict(new File(argv[i+1]));
                            System.exit(0);
                        } else {
                            System.err.println("ERROR: Invalid or option No data root specified!");
                            System.err.println(usage);
                            System.exit(1);
                        }
                    } else if (argv[i].equals("-s")) {
                        if(i+1 < argv.length) {
                            File file = new File(argv[++i]);
                            if (!file.isDirectory()) {
                                System.err.println("ERROR: No such directory: " + file.toString());
                                System.exit(1);
                            }
                            
                            env.setSourceRoot(file);
                        }
                    } else if (argv[i].equals("-i")) {
                        if(i+1 < argv.length) {
                            IgnoredNames.add(argv[++i]);
                        }
                    } else if (argv[i].equals("-S")) {
                        searchRepositories = true;
                    } else if (!argv[i].startsWith("-")) {
                        if (env.getDataRootPath() == null)
                            env.setDataRoot(argv[i]);
                        else
                            subFiles.add(argv[i]);
                    } else {
                        System.err.println(usage);
                        System.exit(1);
                    }
                }
                
                if (env.getDataRootPath()  == null) {
                    System.out.println(usage);
                    System.exit(1);
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
                        System.err.println("ERROR: please specify a SRC_ROOT with option -s !");
                        System.err.println(usage);
                        System.exit(1);
                    }
                    env.setSourceRoot(line);
                    
                    if (!env.getSourceRootFile().isDirectory()) {
                        System.err.println("ERROR: No such directory:" + line);
                        System.err.println(usage);
                        System.exit(1);
                    }
                }
                
                if (! Index.setExuberantCtags(env.getCtags())) {
                    System.exit(1);
                }
                
                if (searchRepositories) {
                    System.out.println("Scanning for repositories...");
                    long start = System.currentTimeMillis();
                    HistoryGuru.getInstance().addExternalRepositories(env.getSourceRootPath());
                    long time = (System.currentTimeMillis() - start) / 1000;
                    System.out.println("Done searching for repositories (" + time + "s)");
                }
                
                if (addProjects) {
                    File files[] = env.getSourceRootFile().listFiles();                    
                    List<Project> projects = env.getProjects();
                    projects.clear();
                    for (File file : files) {
                        if (!file.getName().startsWith(".")) {
                            projects.add(new Project(file.getName(), "/" + file.getName()));
                        }
                    }
                }
                
                if (configFilename != null) {
                    System.out.println("Writing configuration to " + configFilename);
                    System.out.flush();
                    env.writeConfiguration(new File(configFilename));
                    System.out.println("Done...");
                    System.out.flush();
                }
                
                if (refreshHistory) {
                    for (Map.Entry<String, ExternalRepository> entry : RuntimeEnvironment.getInstance().getRepositories().entrySet()) {
                        try {
                            entry.getValue().createCache();
                        } catch (Exception e) {
                            System.err.println("Failed to generate history cache.");
                            e.printStackTrace();
                        }
                    }
                }

                if (runIndex) {
                    Index idx = new Index(verbose ? new StandardPrinter(System.out) : new NullPrinter(), new StandardPrinter(System.err));
                    idx.runIndexer(env.getDataRootFile(), env.getSourceRootFile(), subFiles, env.isGenerateHtml());
                }

                if (configHost != null) {
                    String[] cfg = configHost.split(":");
                    System.out.println("Send configuration to: " + configHost);

                    if (cfg.length == 2) {
                        try {
                            InetAddress host = InetAddress.getByName(cfg[0]);
                            RuntimeEnvironment.getInstance().writeConfiguration(host, Integer.parseInt(cfg[1]));
                        } catch (Exception ex) {
                            System.err.println("Failed to send configuration to " + configHost);
                            ex.printStackTrace();
                        }
                    } else {
                        System.err.println("Syntax error: ");
                        for (String s : cfg) {
                            System.err.print("[" + s + "]");
                        }
                        System.err.println();
                    }
                    System.out.println("Configuration successfully updated");
                }
            } catch (Exception e) {
                System.err.println("Error: [ main ] " + e);
                if (verbose) e.printStackTrace();
                System.exit(1);
            }
        }
    }
}
