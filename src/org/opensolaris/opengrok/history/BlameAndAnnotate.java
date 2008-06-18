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

/* Portions Copyright 2008 Peter Bray */

/*
 * Portions Copyright 2008 Emilio Monti
 *    - For example Executor usage in PerforceRepository
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensolaris.opengrok.util.Executor;

/**
 * BlameAndAnnotate - Annotating RCS & CVS Files via External Commands
 * 
 * Commentary: As far a I can tell the Java RCS infrastructure does not support
 * file annotation. A very quick search on Google showed that "blame" would
 * implement CVS style "annotate" on RCS files. The output from blame-1.3.1
 * and cvs-1.12.13 annotate are identical, so I decided to implement this very
 * simple static interface to parse the output of these commands. Hopefully, 
 * one day it will be replaced with a pure Java implementation.
 * 
 * Blame - http://blame.sourceforge.net/
 * CVS   - http://www.nongnu.org/cvs/
 * 
 * @author Peter Bray <Peter.Darren.Bray@gmail.com>
 */
public class BlameAndAnnotate {

    private final static Pattern ANNOTATION_PATTERN = Pattern.compile("^(\\S+)\\s+\\((\\S+)\\s+(\\S+)\\):\\s(.*)$");

    public static Annotation annotate(String filename, ArrayList<String> command) throws IOException {

        Annotation annotation = new Annotation(filename);

        Executor executor = new Executor(command, null);
        executor.exec();

        BufferedReader outputReader = executor.get_stdout_reader();
        String line;
        int lineNumber = 0;
        try {
            while ((line = outputReader.readLine()) != null) {
                lineNumber++;
                Matcher matcher = ANNOTATION_PATTERN.matcher(line);
                if (matcher.find()) {
                    String revision = matcher.group(1);
                    String author = matcher.group(2);
                    // String date = matcher.group(3);
                    // String content = matcher.group(4);
                    // System.err.println("BlameAndAnnotate.annotate - Line " + lineNumber + " - Revision " + revision + " - Author " + author + " - Date " + date + " for " + content);
                    annotation.addLine(revision, author, true);
                } else {
                    System.err.println("BlameAndAnnotate.annotate - Line " + lineNumber + " - Failed extract annotation from " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return annotation;
    }

    public static Annotation rcsAnnotate(File commaVFile, String filename, String rev) throws IOException {

        // Blame - http://blame.sourceforge.net/

        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add("blame");
        if (rev != null) {
            cmd.add("-r" + rev);
        }
        cmd.add(commaVFile.getAbsolutePath());

        return annotate(filename, cmd);
    }

    public static Annotation cvsAnnotate(File file, String filename, String rev) throws IOException {

        // Testing suggests (as expected) that CVSROOT must be in the environment.

        // If you have Blame, and your code can determine the location of the
        // ,v file you could you rcsAnnotate().

        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add("cvs");
        cmd.add("annotate");
        if (rev != null) {
            cmd.add("-r");

            cmd.add(rev);
        }
        cmd.add(file.getPath());

        return annotate(filename, cmd);
    }

    private static void Usage() {
        System.err.println();
        System.err.println("Usage: org.opensolaris.opengrok.history.BlameAndAnnotate <args>");
        System.err.println("         \"rcs\"|\"cvs\"         Test Type");
        System.err.println("         dataSourceFilename  File to process");
        System.err.println("         labelToUse          Annotation Filename");
        System.err.println("         fileRevision        Which Revision of the File");
        System.err.println();
        System.exit(1);
    }

    public static void main(String[] args) {
        try {
            if (args.length < 3 || args.length > 4) {
                Usage();
            }

            String testType = args[0];
            File dataSource = new File(args[1]);
            String filename = args[2];
            String revision = null;
            if (args.length == 4) {
                revision = args[3];
            }

            Annotation annotation = null;

            if (testType.equalsIgnoreCase("rcs")) {
                annotation = rcsAnnotate(dataSource, filename, revision);
            } else if (testType.equalsIgnoreCase("cvs")) {
                annotation = cvsAnnotate(dataSource, filename, revision);
            } else {
                Usage();
            }

            System.err.println("Annotations for " + annotation.getFilename() + " (" + (revision != null ? revision : "latest") + ")");
            int line = 1;
            String format = "Line %4d : %" + annotation.getWidestRevision() + "s - %s\n";
            while (annotation.getRevision(line).length()>0) {
                System.err.printf(format, line, annotation.getRevision(line), annotation.getAuthor(line));
                line++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
