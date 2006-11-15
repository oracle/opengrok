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
import java.util.*;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
//import org.apache.lucene.search.spell.*;
import org.apache.lucene.spell.NGramSpeller;
import org.opensolaris.opengrok.analysis.*;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.search.scope.MainFrame;
import org.opensolaris.opengrok.web.Util;

/**
 * Creates and updates an inverted source index
 * as well as generates Xref, file stats etc., if specified
 * in the options
 */

public class Indexer {
    private static String ctags = null;
    private static boolean verbose = true;
    private static boolean economical = false;
    private static String usage = "Usage: " +
            "opengrok.jar [-qe] [-c ctagsToUse] [-w webapproot] [-i ignore_name [ -i ..]] [-s SRC_ROOT] DATA_ROOT [subtree .. ]\n" +
            "       opengrok.jar [-O | -l | -t] DATA_ROOT\n" +
            "\t-q run quietly\n" +
            "\t-e economical - consumes less disk space\n" +
            "\t-c path to ctags\n" +
            "\t-w root URL of the webapp, default is /source\n" +
            "\t-i ignore named files or directories\n" +
            "\t-s SRC_ROOT is root directory of source tree\n" +
            "\t   default: last used SRC_ROOT\n" +
            "\tDATA_ROOT - is where output of indexer is stored\n" +
            "\tsubtree - only specified files or directories under SRC_ROOT are processed\n" +
            "\t   if not specified all files under SRC_ROOT are processed\n" +
            "\n\t-O optimize the index \n" +
            "\t-l list all files in the index \n" +
            "\t-t lists tokens occuring more than 5 times. Useful for building a unix dictionary\n" +
            "\n Eg. java -jar opengrok.jar -s /usr/include /var/tmp/opengrok_data rpc";
    
    public static void main(String argv[]) {
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
            String srcRoot = null;
            File srcRootDir = null;
            String dataRoot = null;
            String urlPrefix = "/source/s?";
            ArrayList<String> subFiles = new ArrayList<String>();
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
                    } else if (argv[i].equals("-q")) {
                        verbose = false;
                    } else if (argv[i].equals("-e")) {
                        economical = true;
                    } else if (argv[i].equals("-c")) {
                        if(i+1 < argv.length) {
                            ctags = argv[++i];
                            System.setProperty("ctags", ctags);
                        }
                    } else if (argv[i].equals("-w")) {
                        if(i+1 < argv.length) {
                            String webapp = argv[++i];
                            if(webapp.startsWith("/") || webapp.startsWith("http")) {
                            } else {
                                webapp = "/" + webapp;
                            }
                            if(webapp.endsWith("/")) {
                                urlPrefix = webapp + "s?";
                            } else {
                                urlPrefix = webapp + "/s?";
                            }
                        }
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
                            srcRoot = argv[++i];
                            srcRootDir = new File(srcRoot);
                            srcRoot = srcRootDir.getCanonicalPath();
                            srcRootDir = srcRootDir.getCanonicalFile();
                            if(!srcRootDir.isDirectory()) {
                                System.err.println("ERROR: No such directory:" + srcRoot);
                                System.err.println(usage);
                                System.exit(1);
                            }
                        }
                    } else if (argv[i].equals("-i")) {
                        if(i+1 < argv.length) {
                            IgnoredNames.ignore.add(argv[++i]);
                        }
                    } else if (!argv[i].startsWith("-")) {
                        if (dataRoot == null)
                            dataRoot = argv[i];
                        else
                            subFiles.add(argv[i]);
                    } else {
                        System.err.println(usage);
                        System.exit(1);
                    }
                }
                System.setProperty("urlPrefix", urlPrefix);
                if (dataRoot == null) {
                    System.out.println(usage);
                    System.exit(1);
                }
                if (srcRoot == null) {
                    File srcConfig = new File(dataRoot, "SRC_ROOT");
                    if(srcConfig.exists()) {
                        try {
                            BufferedReader sr = new BufferedReader(new FileReader(srcConfig));
                            srcRoot = sr.readLine();
                            sr.close();
                        } catch (IOException e) {
                        }
                    }
                    if(srcRoot == null) {
                        System.err.println("ERROR: please specify a SRC_ROOT with option -s !");
                        System.err.println(usage);
                        System.exit(1);
                    }
                    srcRootDir = new File(srcRoot);
                    if(!srcRootDir.isDirectory()) {
                        System.err.println("ERROR: No such directory:" + srcRoot);
                        System.err.println(usage);
                        System.exit(1);
                    }
                }
                if (! Index.setExuberantCtags(ctags)) {
                    System.exit(1);
                }
                Index idx = new Index(verbose ? new StandardPrinter(System.out) : new NullPrinter(), new StandardPrinter(System.err));
                idx.runIndexer(new File(dataRoot), srcRootDir, subFiles, economical);
            } catch ( Exception e) {
                System.err.println("Error: [ main ] " + e);
                if (verbose) e.printStackTrace();
                System.exit(1);
            }
        }
    }
}
