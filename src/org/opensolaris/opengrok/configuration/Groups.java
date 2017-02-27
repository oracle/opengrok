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
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.opensolaris.opengrok.util.Getopt;

/**
 *
 * @author Krystof Tulinger
 */
public final class Groups {

    private interface Walker {

        /**
         * @param g group
         * @return true if traversing should end, false otherwise
         */
        boolean call(Group g);
    }

    public static void main(String[] argv) {
        PrintStream out = System.out;
        File outFile = null;
        Configuration cfg = new Configuration();
        String groupname = null;
        String grouppattern = null;
        String parent = null;
        boolean list = false;
        boolean delete = false;
        boolean empty = false;
        String match = null;

        Getopt getopt = new Getopt(argv, "dehi:lm:n:o:p:r:?");

        try {
            getopt.parse();
        } catch (ParseException ex) {
            System.err.println("Groups: " + ex.getMessage());
            usage();
            System.exit(1);
        }

        try {
            int cmd;
            File f;
            getopt.reset();
            while ((cmd = getopt.getOpt()) != -1) {
                switch (cmd) {
                    case 'd':
                        delete = true;
                        break;
                    case 'e':
                        empty = true;
                        break;
                    case 'h':
                        usage();
                        System.exit(0);
                        break;
                    case 'i':
                        f = new File(getopt.getOptarg());
                        try {
                            cfg = Configuration.read(f);
                        } catch (ArrayIndexOutOfBoundsException ex) {
                            System.err.println("An error occured - this may mean that the input file is not well-formated.");
                            System.err.println();
                            ex.printStackTrace(System.err);
                            System.exit(3);
                        }
                        break;
                    case 'l':
                        list = true;
                        break;
                    case 'm':
                        match = getopt.getOptarg();
                        break;
                    case 'n':
                        groupname = getopt.getOptarg();
                        break;
                    case 'o':
                        outFile = new File(getopt.getOptarg());
                        break;
                    case 'p':
                        parent = getopt.getOptarg();
                        break;
                    case 'r':
                        grouppattern = getopt.getOptarg();
                        break;
                    case '?':
                        usage();
                        System.exit(0);
                        break;
                    default:
                        System.err.println("Internal Error - Not implemented option: " + (char) cmd);
                        usage();
                        System.exit(1);
                        break;
                }
            }
        } catch (FileNotFoundException ex) {
            System.err.println("An error occured - file does not exist");
            ex.printStackTrace(System.err);
            System.exit(3);
        } catch (IOException ex) {
            System.err.println("An uknown error occured - the input file may be corrupted");
            ex.printStackTrace(System.err);
            System.exit(3);
        }

        if (match != null) {
            // perform matching
            if (parent != null || groupname != null || grouppattern != null) {
                System.err.println("Match option should be used without parent|groupname|groupregex options");
                usage();
                System.exit(1);
            }
            matchGroups(System.out, cfg.getGroups(), match);
        } else if (empty) {
            // just list the groups
            if (parent != null || groupname != null || grouppattern != null) {
                System.err.println("Match option should be used without parent|groupname|groupregex options");
                usage();
                System.exit(1);
            }
            printOut(false, cfg, out);
        } else if (delete) {
            // perform delete
            if (parent != null || grouppattern != null) {
                System.err.println("Delete option should be used without parent|groupregex options");
                usage();
                System.exit(1);
            }
            if (groupname == null) {
                System.err.println("You must specify the group name");
                usage();
                System.exit(1);
            }
            deleteGroup(cfg.getGroups(), groupname);
            out = prepareOutput(outFile);
            printOut(list, cfg, out);
        } else if (groupname != null && grouppattern != null) {
            // perform insert/update. parent may be null
            if (!modifyGroup(cfg.getGroups(), groupname, grouppattern, parent)) {
                System.err.println("Parent group does not exist \"" + parent + "\"");
            } else {
                out = prepareOutput(outFile);
                printOut(list, cfg, out);
            }
        } else if (list) {
            // just list the groups
            if (groupname != null) {
                System.err.println("List option should be used without groupname options");
                usage();
                System.exit(1);
            }
            printOut(list, cfg, out);
        } else {
            System.err.println("Wrong combination of options. See usage.");
            usage();
            System.exit(2);
        }
    }

    /**
     * Prints the configuration to the stream.
     *
     * @param list if true then it lists all available groups in configuration
     * if @param out is different than stdout it also prints the current
     * configuration to that stream otherwise it prints the configuration to the
     * @param out stream.
     * @param cfg configuration
     * @param out output stream
     */
    private static void printOut(boolean list, Configuration cfg, PrintStream out) {
        if (list) {
            listGroups(System.out, cfg.getGroups());
            if (out != System.out) {
                out.print(cfg.getXMLRepresentationAsString());
            }
        } else {
            out.print(cfg.getXMLRepresentationAsString());
        }
    }

    private static PrintStream prepareOutput(File outFile) {
        PrintStream out = System.out;
        if (outFile != null) {
            try {
                out = new PrintStream(outFile, "utf-8");
            } catch (FileNotFoundException ex) {
                System.err.println("An error occured - file does not exist");
                ex.printStackTrace(System.err);
                System.exit(3);
            } catch (UnsupportedEncodingException ex) {
                System.err.println("An error occured - file contains unsupported charset");
                ex.printStackTrace(System.err);
                System.exit(3);
            }
        }
        return out;
    }

    /**
     * List groups given as a parameter.
     *
     * @param out stream
     * @param groups groups
     */
    private static void listGroups(PrintStream out, Set<Group> groups) {
        treeTraverseGroups(groups, new Walker() {
            @Override
            public boolean call(Group g) {
                for (int i = 0; i < g.getFlag() * 2; i++) {
                    out.print(" ");
                }
                out.println(g.getName() + " ~ \"" + g.getPattern() + "\"");
                return false;
            }
        });
    }

    /**
     * Finds groups which would match the project.
     *
     * @param out stream to write the results
     * @param groups set of groups
     * @param match project description
     */
    private static void matchGroups(PrintStream out, Set<Group> groups, String match) {
        Project p = new Project();
        p.setName(match);

        List<Group> matched = new ArrayList<>();
        linearTraverseGroups(groups, new Walker() {
            @Override
            public boolean call(Group g) {
                if (g.match(p)) {
                    matched.add(g);
                }
                return false;
            }
        });

        out.println(matched.size() + " group(s) match(es) the \"" + match + "\"");
        for (Group g : matched) {
            out.println(g.getName() + " \"" + g.getPattern() + "\"");
        }
    }

    /**
     * Adds a group into the xml tree.
     *
     * If group already exists, only the pattern is modified. Parent group can
     * be null, in that case a new group is inserted as a top level group.
     *
     * @param groups existing groups
     * @param groupname new group name
     * @param grouppattern new group pattern
     * @param parent parent
     * @return false if parent group was not found, true otherwise
     */
    private static boolean modifyGroup(Set<Group> groups, String groupname, String grouppattern, String parent) {
        Group g = new Group();
        g.setName(groupname);
        g.setPattern(grouppattern);

        if (updateGroup(groups, groupname, grouppattern)) {
            return true;
        }

        if (parent != null) {
            if (insertToParent(groups, parent, g)) {
                groups.add(g);
                return true;
            }
            return false;
        }

        groups.add(g);
        return true;
    }

    /**
     * Removes group from the xml tree.
     *
     * @param groups existing groups
     * @param groupname group to remove
     */
    private static void deleteGroup(Set<Group> groups, String groupname) {
        for (Group g : groups) {
            if (g.getName().equals(groupname)) {
                groups.remove(g);
                groups.removeAll(g.getDescendants());
                return;
            }
        }
    }

    private static boolean treeTraverseGroups(Set<Group> groups, Walker f) {
        LinkedList<Group> stack = new LinkedList<>();
        for (Group g : groups) {
            g.setFlag(0);
            if (g.getParent() == null) {
                stack.add(g);
            }
        }

        while (!stack.isEmpty()) {
            Group g = stack.getFirst();
            stack.removeFirst();

            if (f.call(g)) {
                return true;
            }

            for (Group x : g.getSubgroups()) {
                x.setFlag(g.getFlag() + 1);
                stack.addFirst(x);
            }
        }
        return false;
    }

    private static boolean linearTraverseGroups(Set<Group> groups, Walker f) {
        for (Group g : groups) {
            if (f.call(g)) {
                return true;
            }
        }
        return false;
    }

    private static boolean insertToParent(Set<Group> groups, String parent, Group g) {
        return linearTraverseGroups(groups, new Walker() {
            @Override
            public boolean call(Group x) {
                if (x.getName().equals(parent)) {
                    x.addGroup(g);
                    Group tmp = x.getParent();
                    while (tmp != null) {
                        tmp.addDescendant(g);
                        tmp = tmp.getParent();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private static boolean updateGroup(Set<Group> groups, String groupname, String grouppattern) {
        return linearTraverseGroups(groups, new Walker() {
            @Override
            public boolean call(Group g) {
                if (g.getName().equals(groupname)) {
                    g.setPattern(grouppattern);
                    return true;
                }
                return false;
            }
        });
    }

    private static final void usage() {
        System.err.println("Usage:");
        System.err.println("Groups.java" + " [OPTIONS]");
        System.err.println();
        System.err.println("OPTIONS:");
        System.err.println("Help");
        System.err.println("-?                   print this help message");
        System.err.println("-h                   print this help message");
        System.err.println("-v                   verbose/debug mode");
        System.err.println();
        System.err.println("Input/Output");
        System.err.println("-i /path/to/file     input file|default is empty configuration");
        System.err.println("-o /path/to/file     output file|default is stdout");
        System.err.println();
        System.err.println("Listing");
        System.err.println("-m <project name>    performs matching based on given project name");
        System.err.println("-l                   lists all available groups in input file");
        System.err.println("-e                   creates an empty configuration or");
        System.err.println("                     directly outputs the input file (if given)");
        System.err.println();
        System.err.println("Modification");
        System.err.println("-n <group name>      specify group name which should be inserted|updated (requires either -r or -d option)");
        System.err.println("-r <group regex>     specify group regex pattern (requires -n option)");
        System.err.println("-p <parent group>    optional parameter for the parent group name (requires -n option)");
        System.err.println("-d                   delete specified group (requires -n option)");
        System.err.println();
        System.err.println("NOTE: using modification options with -l forces the program to list all\n"
                + "available groups after the modification. Output to a file can still be used.");
        System.err.println();
        System.err.println("Examples");
        System.err.println("-i ~/c.xml -l                     # => list groups");
        System.err.println("-n Abcd -r \"abcd.*\" -o ~/c.xml    # => add group and print to ~/c.xml");
        System.err.println("-i ~/c.xml -m abcdefg             # => prints groups which would match this project description");
        System.err.println("-i ~/c.xml -d -n Abcd             # => deletes group Abcd");
        System.err.println("-n Bcde -r \".*bcde.*\" -l          # => add group and lists the result");
    }
}
