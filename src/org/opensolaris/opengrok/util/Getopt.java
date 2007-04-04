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
package org.opensolaris.opengrok.util;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple implementation of the getopt(3c). It does just implement what I
 * need ;-) Please note that I dislike the way GNU getopt allows mixing of
 * options and arguments, so this version will stop processing options as soon
 * as it encounters an argument.
 *
 */
public class Getopt {
    class Option {
        char option;
        String argument;
    };
    
    private ArrayList<Option> options;
    private int current;
    private int optind;
    private String[] argv;
    private String opts;
    
    /**
     * Creates a new instance of Getopt
     * @param argv argument vector
     * @param opts the list of allowed options
     */
    public Getopt(String[] argv, String opts) {
        options = new ArrayList<Option>();
        current = -1;
        optind = -1;
        this.argv = argv;
        this.opts = opts;
    }
    
    /**
     * Parse the command lineoptions
     * @throws ParseException if an illegal argument is passed
     */
    public void parse() throws ParseException {
        
        int ii = 0;
        while (ii < argv.length) {
            char[] chars = argv[ii].toCharArray();
            if (chars.length > 0) {
                
                if (chars[0] == '-') {
                    if (argv[ii].equals("--")) {
                        // End of command line options ;)
                        optind = ii + 1;
                        break;
                    }
                    
                    for (int jj = 1; jj < chars.length; ++jj) {
                        int idx = opts.indexOf(chars[jj]);
                        if (idx == -1) {
                            throw new ParseException("Unknown argument: " + argv[ii].substring(jj), ii);
                        }
                        
                        Option option = new Option();
                        option.option = chars[jj];
                        options.add(option);
                        // does this option take an argument?
                        if ((idx + 1) < opts.length() && (opts.charAt(idx + 1) ==':')) {
                            // next should be an argument
                            if ((jj + 1) < chars.length) {
                                // Rest of this is the argument
                                option.argument = argv[ii].substring(jj + 1);
                                break;
                            } else {
                                // next argument vector contains the argument
                                ++ii;
                                if (ii < argv.length) {
                                    option.argument = argv[ii];
                                } else {
                                    throw new ParseException("Option " + chars[jj] + " requires an argument", ii);
                                }
                            }
                        }
                    }
                    ++ii;
                } else {
                    // End of options
                    optind = ii;
                    break;
                }
            }
        }
    }
    
    /**
     * Get the next option in the options string.
     * @return the next valid option, or -1 if all options are processed
     */
    public int getOpt() {
        int ret = -1;
        
        ++current;
        if (current < options.size()) {
            ret = options.get(current).option;
        }
        
        return ret;
    }
    
    /**
     * Get the argument to the current option
     * @return the argument or null if none present (or allowed)
     */
    public String getOptarg() {
        String ret = null;
        
        if (current < options.size()) {
            ret = options.get(current).argument;
        }
        return ret;
    }
    
    /**
     * Get the index of the first argument
     * @return the index of the first argument in the original array
     */
    public int getOptind() {
        return optind;
    }
    
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Getopt opts = new Getopt(args, "a:b:c");
        try {
            opts.parse();
        } catch (ParseException ex) {
            Logger.getLogger("global").log(Level.SEVERE, null, ex);
            System.exit(1);
        }
        
        System.out.println("Options and their arguments");
        int cmd;
        while ((cmd = opts.getOpt()) != -1) {
            String arg = opts.getOptarg();
            if (arg != null) {
                System.out.println("" + (char)cmd + " " + arg);
            } else {
                System.out.println((char)cmd);
            }
        }
        System.out.println("Arguments");
        for (int ii = opts.getOptind(); ii < args.length; ++ii) {
            System.out.println(args[ii]);
        }
    }
}
