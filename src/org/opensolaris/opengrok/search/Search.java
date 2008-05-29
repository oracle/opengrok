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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.search;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.search.scope.SearchEngine;
import org.opensolaris.opengrok.util.Getopt;

/**
 * Search and list the matching files
 */
class Search {

    /**
     * usage Search index "query" prunepath
     */
    public static void main(String[] argv) {
        String usage = "USAGE: Search -R <configuration.xml> [-d | -r | -p | -h | -f] 'query string' ..\n" 
                + "\t -R <configuration.xml> Read configuration from the specified file\n" 
                + "\t -d Symbol Definitions\n" 
                + "\t -r Symbol References\n" 
                + "\t -p Path\n" 
                + "\t -h History\n"  
                + "\t -f Full text";

        SearchEngine engine = new SearchEngine();
        boolean config = false;

        Getopt getopt = new Getopt(argv, "R:d:r:p:h:f:");
        try {
            getopt.parse();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println(usage);
            System.exit(1);
        }
        
        int cmd;
        while ((cmd = getopt.getOpt()) != -1) {
            switch (cmd) {
                case 'R':
                    try {
                        RuntimeEnvironment.getInstance().readConfiguration(new File(getopt.getOptarg()));
                        config = true;
                    } catch (Exception e) {
                        System.err.println("Failed to read config file: ");
                        e.printStackTrace();
                        System.exit(1);
                    }
                    break;
                case 'd':
                    engine.setDefinition(getopt.getOptarg());
                    break;
                case 'r':
                    engine.setSymbol(getopt.getOptarg());
                    break;
                case 'p':
                    engine.setFile(getopt.getOptarg());
                    break;
                case 'h':
                    engine.setHistory(getopt.getOptarg());
                    break;
                case 'f' :
                    engine.setFreetext(getopt.getOptarg());
                    break;
                    
                default:
                    System.err.println("Unknown option: " + (char) cmd);
                    System.err.println(usage);
                    System.exit(1);
            }
        }

        if (!config) {
            System.err.println("You must specify a configuration file");
            System.err.println(usage);
            System.exit(1);
        }
        
        if (!engine.isValidQuery()) {
            System.err.println("You did not specify a valid query");
            System.err.println(usage);
            System.exit(1);
        }
        
        int nhits = engine.search();
        if (nhits == 0) {
            System.err.println("Your search \"" + engine.getQuery() + "\" did not match any files.");
        } else {
            List<Hit> hits = new ArrayList<Hit>();
            engine.more(0, nhits, hits);
            String root = RuntimeEnvironment.getInstance().getSourceRootPath();
            for (Hit hit : hits) {
                File file = new File(root, hit.getFilename());
                System.out.println(file.getAbsolutePath() + ": [" + hit.getLine() + "]");
            }
        }
    }

    private Search() {
    }
}
