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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Portions Copyright 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.index;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class CommandLineOptions {

    private final static String ON_OFF = "on/off";

    static class Option {

        char option;
        String argument;
        String description;

        public Option(char opt, String arg, String descr) {
            option = opt;
            argument = arg;
            description = descr;
        }

        public String getUsage() {
            StringBuilder sb = new StringBuilder();
            sb.append('-');
            sb.append(option);
            if (argument != null) {
                sb.append(' ');
                sb.append(argument);
            }
            sb.append("\n\t");
            sb.append(description);

            return sb.toString();
        }
    }
    private final List<Option> options;

    public CommandLineOptions() {
        options = new ArrayList<Option>();
        options.add(new Option('?', null, "Help"));
        options.add(new Option('A', "ext:analyzer", "Files with the named extension should be analyzed with the specified class"));
        options.add(new Option('a', ON_OFF, "Allow or disallow leading wildcards in a search"));
        options.add(new Option('B', "url", "Base URL of the user Information provider. Default: \"http://www.opensolaris.org/viewProfile.jspa?username=\""));
        options.add(new Option('C', null, "Print per project percentage progress information(I/O extensive, since one read through dir structure is made before indexing, needs -v, otherwise it just goes to the log)"));
        options.add(new Option('c', "/path/to/ctags", "Path to Exuberant Ctags from http://ctags.sf.net by default takes the Exuberant Ctags in PATH."));
        options.add(new Option('D', null, "Store history cache in a database (needs the JDBC driver in the classpath, typically derbyclient.jar or derby.jar)"));
        options.add(new Option('d', "/path/to/data/root", "The directory where OpenGrok stores the generated data"));
        options.add(new Option('e', null, "Economical - consumes less disk space. It does not generate hyper text cross reference files offline, but will do so on demand - which could be sightly slow."));
        options.add(new Option('H', null, "Generate history cache for all repositories"));
        options.add(new Option('h', "/path/to/repository", "just generate history cache for the specified repos (absolute path from source root)"));
        options.add(new Option('I', "pattern", "Only files matching this pattern will be examined (supports wildcards, example: -I *.java -I *.c)"));
        options.add(new Option('i', "pattern", "Ignore the named files or directories (supports wildcards, example: -i *.so -i *.dll)"));
        options.add(new Option('j', "class", "Name of the JDBC driver class used by the history cache. Can use one of the shorthands \"client\" (org.apache.derby.jdbc.ClientDriver) or \"embedded\" (org.apache.derby.jdbc.EmbeddedDriver). Default: \"client\""));
        options.add(new Option('k', "/path/to/repository", "Kill the history cache for the given repository and exit. Use '*' to delete the cache for all repositories."));
        options.add(new Option('K', null, "List all repository pathes and exit."));
        options.add(new Option('L', "path", "Path to the subdirectory in the web-application containing the requested stylesheet. The following factory-defaults exist: \"default\", \"offwhite\" and \"polished\""));
        options.add(new Option('l', ON_OFF, "Turn on/off locking of the Lucene database during index generation"));
        options.add(new Option('m', "number", "The maximum words to index in a file"));
        options.add(new Option('N', "/path/to/symlink", "Allow this symlink to be followed. Option may be repeated."));
        options.add(new Option('n', null, "Do not generate indexes, but process all other command line options"));
        options.add(new Option('O', ON_OFF, "Turn on/off the optimization of the index database as part of the indexing step"));
        options.add(new Option('P', null, "Generate a project for each of the top-level directories in source root"));
        options.add(new Option('p', "/path/to/default/project", "This is the path to the project that should be selected by default in the web application(when no other project set either in cookie or in parameter). You should strip off the source root."));
        options.add(new Option('Q', ON_OFF, "Turn on/off quick context scan. By default only the first 32k of a file is scanned, and a '[..all..]' link is inserted if the file is bigger. Activating this may slow the server down (Note: this is setting only affects the web application)"));
        options.add(new Option('q', null, "Run as quietly as possible"));
        options.add(new Option('R', "/path/to/configuration", "Read configuration from the specified file"));
        options.add(new Option('r', ON_OFF, "Turn on/off support for remote SCM systems"));
        options.add(new Option('S', null, "Search for \"external\" source repositories and add them"));
        options.add(new Option('s', "/path/to/source/root", "The root directory of the source tree"));
        options.add(new Option('T', "number", "The number of threads to use for index generation. By default the number of threads will be set to the number of available CPUs"));
        options.add(new Option('t', "number", "Default tabsize to use (number of spaces per tab character)"));
        options.add(new Option('U', "host:port", "Send the current configuration to the specified address (This is most likely the web-app configured with ConfigAddress)"));
        options.add(new Option('u', "url", "URL to the database that contains the history cache. Default: If -j specifies \"embedded\", \"jdbc:derby:$DATA_ROOT/cachedb;create=true\"; otherwise, \"jdbc:derby://localhost/cachedb;create=true\""));
        options.add(new Option('V', null, "Print version and quit"));
        options.add(new Option('v', null, "Print progress information as we go along"));
        options.add(new Option('W', "/path/to/configuration", "Write the current configuration to the specified file (so that the web application can use the same configuration"));
        options.add(new Option('w', "webapp-context", "Context of webapp. Default is /source. If you specify a different name, make sure to rename source.war to that name."));
        options.add(new Option('X', "url:suffix", "URL Suffix for the user Information provider. Default: \"\""));
        options.add(new Option('z', "number", "depth of scanning for repositories in directory structure relative to source root"));
    }

    public String getCommandString() {
        StringBuilder sb = new StringBuilder();
        for (Option o : options) {
            sb.append(o.option);
            if (o.argument != null) {
                sb.append(':');
            }
        }
        return sb.toString();
    }

    public String getCommandUsage(char c) {
        for (Option o : options) {
            if (o.option == c) {
                return o.getUsage();
            }
        }

        return null;
    }

    private void spool(BufferedReader reader, PrintWriter out, String tag) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.equals(tag)) {
                return;
            }
            out.println(line);
        }
    }

    public String getUsage() {
        StringWriter wrt = new StringWriter();
        PrintWriter out = new PrintWriter(wrt);

        out.println("Usage: opengrok.jar [options]");
        for (Option o : options) {
            out.println(o.getUsage());
        }

        out.flush();
        out.close();

        return wrt.toString();
    }

    public String getManPage() throws IOException {
        StringWriter wrt = new StringWriter();
        PrintWriter out = new PrintWriter(wrt);

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("opengrok.xml"), "US-ASCII"));

        spool(reader, out, "___INSERT_DATE___");
        out.print("<refmiscinfo class=\"date\">");
        out.print(DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date()));
        out.println("</refmiscinfo>");

        spool(reader, out, "___INSERT_USAGE___");
        for (Option o : options) {
            out.println("<optional><option>");
            out.print(o.option);
            if (o.argument != null) {
                out.print(" <replaceable>");
                out.print(o.argument);
                out.print("</replaceable>");
            }
            out.println("</option></optional>");
        }

        spool(reader, out, "___INSERT_OPTIONS___");
        for (Option o : options) {
            out.print("<varlistentry><term><option>");
            out.print(o.option);
            out.print("</option></term><listitem><para>");
            out.print(o.description);
            out.println("</para></listitem></varlistentry>");
        }

        spool(reader, out, "___END_OF_FILE___");
        out.flush();
        reader.close();

        return wrt.toString();
    }

    /**
     * Not intended for normal use, but for the JUnit test suite to validate
     * that all options contains a description :-)
     *
     * @return an iterator to iterate through all of the command line options
     */
    Iterator<Option> getOptionsIterator() {
        return options.iterator();
    }

   /**
    * Print out a manual page on standard out. Used for building manual page.
    *
    * @param argv argument vector. not used.
    */
   @SuppressWarnings("PMD.SystemPrintln")
   public static void main(String[] argv) {
       CommandLineOptions co = new CommandLineOptions();
       try {
           System.out.println(co.getManPage());
       } catch (IOException exp) {
           System.err.println(exp.getLocalizedMessage());
       }
   }
}
