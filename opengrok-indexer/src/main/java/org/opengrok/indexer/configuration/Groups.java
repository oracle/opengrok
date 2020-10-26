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
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.configuration;

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
import org.opengrok.indexer.util.Getopt;

/**
 *
 * @author Krystof Tulinger
 */
public final class Groups {

    /**
     * Interface used to perform an action to a single group.
     */
    private interface Walker {

        /**
         * @param g group
         * @return true if traversing should stop just after this group, false
         * otherwise
         */
        boolean call(Group g);
    }

    private Groups() {
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
            usage(System.err);
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
                        usage(System.out);
                        System.exit(0);
                        break;
                    case 'i':
                        f = new File(getopt.getOptarg());
                        try {
                            cfg = Configuration.read(f);
                        } catch (ArrayIndexOutOfBoundsException ex) {
                            System.err.println("An error occurred - this may mean that the input file is not well-formated.");
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
                        usage(System.out);
                        System.exit(0);
                        break;
                    default:
                        System.err.println("Internal Error - Not implemented option: " + (char) cmd);
                        usage(System.err);
                        System.exit(1);
                        break;
                }
            }
        } catch (FileNotFoundException ex) {
            System.err.println("An error occurred - file does not exist");
            ex.printStackTrace(System.err);
            System.exit(3);
        } catch (IOException ex) {
            System.err.println("An unknown error occurred - the input file may be corrupted");
            ex.printStackTrace(System.err);
            System.exit(3);
        }

        if (match != null) {
            // perform matching
            if (parent != null || groupname != null || grouppattern != null) {
                System.err.println("Match option should be used without parent|groupname|groupregex options");
                usage(System.err);
                System.exit(1);
            }
            matchGroups(System.out, cfg.getGroups(), match);
        } else if (empty) {
            // just list the groups
            if (parent != null || groupname != null || grouppattern != null) {
                System.err.println("Match option should be used without parent|groupname|groupregex options");
                usage(System.err);
                System.exit(1);
            }
            out = prepareOutput(outFile);
            printOut(false, cfg, out);
        } else if (delete) {
            // perform delete
            if (parent != null || grouppattern != null) {
                System.err.println("Delete option should be used without parent|groupregex options");
                usage(System.err);
                System.exit(1);
            }
            if (groupname == null) {
                System.err.println("You must specify the group name");
                usage(System.err);
                System.exit(1);
            }
            deleteGroup(cfg.getGroups(), groupname);
            out = prepareOutput(outFile);
            printOut(list, cfg, out);
        } else if (groupname != null) {
            if (grouppattern == null) {
                grouppattern = "";
            }
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
                usage(System.err);
                System.exit(1);
            }
            printOut(list, cfg, out);
        } else {
            System.err.println("Wrong combination of options. See usage.");
            usage(System.err);
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
                System.err.println("An error occurred - file does not exist");
                ex.printStackTrace(System.err);
                System.exit(3);
            } catch (UnsupportedEncodingException ex) {
                System.err.println("An error occurred - file contains unsupported charset");
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
                out.println(g.getName() + " ~ '" + g.getPattern() + "'");
                return false;
            }
        });
    }

    /**
     * Finds groups which would match the project.
     *
     * @param out stream to write the results
     * @param groups set of groups
     * @param match project name
     */
    private static void matchGroups(PrintStream out, Set<Group> groups, String match) {
        Project p = new Project(match);

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
            out.println(g.getName() + " '" + g.getPattern() + "'");
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
        Group g = new Group(groupname, grouppattern);

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

    /**
     * Traverse the set of groups starting in top level groups (groups without a
     * parent group) and performing depth first search in the group's subgroups.
     *
     * @param groups set of groups (mixed top level and other groups)
     * @param walker an instance of {@link Walker} which is used for every
     * traversed group
     * @return true if {@code walker} emits true for any of the groups; false
     * otherwise
     *
     * @see Walker
     */
    private static boolean treeTraverseGroups(Set<Group> groups, Walker walker) {
        LinkedList<Group> stack = new LinkedList<>();
        for (Group g : groups) {
            // the flag here represents the group's depth in the group tree
            g.setFlag(0);
            if (g.getParent() == null) {
                stack.addLast(g);
            }
        }

        while (!stack.isEmpty()) {
            Group g = stack.getFirst();
            stack.removeFirst();

            if (walker.call(g)) {
                return true;
            }

            g.getSubgroups().forEach((x) -> x.setFlag(g.getFlag() + 1));
            // add all the subgroups respecting the sorted order
            stack.addAll(0, g.getSubgroups());
        }
        return false;
    }

    /**
     * Traverse the set of groups linearly based on the set's iterator.
     *
     * @param groups set of groups (mixed top level and other groups)
     * @param walker an instance of {@link Walker} which is used for every
     * traversed group
     * @return true if {@code walker} emits true for any of the groups; false
     */
    private static boolean linearTraverseGroups(Set<Group> groups, Walker walker) {
        for (Group g : groups) {
            if (walker.call(g)) {
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

    private static void usage(PrintStream out) {
        out.println("Usage:");
        out.println("Groups.java" + " [OPTIONS]");
        out.println();
        out.println("OPTIONS:");
        out.println("Help");
        out.println("-?                   print this help message");
        out.println("-h                   print this help message");
        out.println("-v                   verbose/debug mode");
        out.println();
        out.println("Input/Output");
        out.println("-i /path/to/file     input file|default is empty configuration");
        out.println("-o /path/to/file     output file|default is stdout");
        out.println();
        out.println("Listing");
        out.println("-m <project name>    performs matching based on given project name");
        out.println("-l                   lists all available groups in input file");
        out.println("-e                   creates an empty configuration or");
        out.println("                     directly outputs the input file (if given)");
        out.println();
        out.println("Modification");
        out.println("-n <group name>      specify group name which should be inserted|updated (requires either -r or -d option)");
        out.println("-r <group regex>     specify group regex pattern (requires -n option)");
        out.println("-p <parent group>    optional parameter for the parent group name (requires -n option)");
        out.println("-d                   delete specified group (requires -n option)");
        out.println();
        out.println("NOTE: using modification options with -l forces the program to list all\n"
                + "available groups after the modification. Output to a file can still be used.");
        out.println();
        out.println("Examples");
        out.println("-i ~/c.xml -l                     # => list groups");
        out.println("-n Abcd -r \"abcd.*\" -o ~/c.xml    # => add group and print to ~/c.xml");
        out.println("-i ~/c.xml -m abcdefg             # => prints groups which would match this project description");
        out.println("-i ~/c.xml -d -n Abcd             # => deletes group Abcd");
        out.println("-n Bcde -r \".*bcde.*\" -l          # => add group and lists the result");
    }
}
