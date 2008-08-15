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

package org.opensolaris.opengrok.history;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.util.Executor;

/**
 * Access to a Perforce repository
 * 
 * @author Emilio Monti - emilmont@gmail.com
 */
public class PerforceRepository extends Repository {

    private static class PerforceChecker {

        private boolean haveP4;

        PerforceChecker() {
            Process process = null;
            try {
                String argv[] = {System.getProperty("org.opensolaris.opengrok.history.Perforce", "p4"), "help"};
                process = Runtime.getRuntime().exec(argv);
                boolean done = false;
                do {
                    try {
                        process.waitFor();
                        done = true;
                    } catch (InterruptedException exp) {
                        // Ignore
                    }
                } while (!done);
                if (process.exitValue() == 0) {
                    haveP4 = true;
                }
            } catch (IOException exp) {

            } finally {
                // Clean up zombie-processes...
                if (process != null) {
                    try {
                        process.exitValue();
                    } catch (IllegalThreadStateException exp) {
                        // the process is still running??? just kill it..
                        process.destroy();
                    }
                }
            }
        }
    }
    private static PerforceChecker checker = new PerforceChecker();
    private final static Pattern annotation_pattern = Pattern.compile("^(\\d+): .*");

    public Annotation annotate(File file, String rev) throws IOException {
        Annotation a = new Annotation(file.getName());

        List<HistoryEntry> revisions = PerforceHistoryParser.getRevisions(file, rev);
        HashMap<String, String> revAuthor = new HashMap<String, String>();
        for (HistoryEntry entry : revisions) {
            // a.addDesc(entry.getRevision(), entry.getMessage());
            revAuthor.put(entry.getRevision(), entry.getAuthor());
        }

        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add("p4");
        cmd.add("annotate");
        cmd.add("-q");
        cmd.add(file.getPath() + ((rev != null) ? ("#" + rev) : ("")));

        Executor executor = new Executor(cmd, file.getParentFile());
        executor.exec();

        BufferedReader output_reader = executor.get_stdout_reader();
        String line;
        int lineno = 0;
        try {
            while ((line = output_reader.readLine()) != null) {
                ++lineno;
                Matcher matcher = annotation_pattern.matcher(line);
                if (matcher.find()) {
                    String revision = matcher.group(1);
                    String author = revAuthor.get(revision);
                    a.addLine(revision, author, true);
                } else {
                    OpenGrokLogger.getLogger().log(Level.SEVERE,
                            "Error: did not find annotation in line " + lineno + ": [" + line + "]");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return a;
    }

    @Override
    Class<? extends HistoryParser> getDirectoryHistoryParser() {
        return PerforceHistoryParser.class;
    }

    @Override
    InputStream getHistoryGet( String parent,  String basename,  String rev) {
        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add("p4");
        cmd.add("print");
        cmd.add("-q");
        cmd.add(basename + ((rev != null) ? ("#" + rev) : ("")));
        Executor executor = new Executor(cmd, new File(parent));
        executor.exec();
        return new ByteArrayInputStream(executor.get_stdout().getBytes());
    }

    @Override
    void update() throws Exception {
    /* TODO */
    }

    @Override
    Class<? extends HistoryParser> getHistoryParser() {
        return PerforceHistoryParser.class;
    }

    @Override
    boolean fileHasHistory( File file) {
        return true;
    }

    @Override
    boolean isCacheable() {
        return true;
    }

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
    }

    /**
     * Check if a given file is in the depot
     * 
     * @param file The file to test
     * @return true if the given file is in the depot, false otherwise
     */
    public static boolean isInP4Depot(File file) {
        if (checker.haveP4) {
            ArrayList<String> cmd = new ArrayList<String>();
            cmd.add("p4");
            if (file.isDirectory()) {
                cmd.add("dirs");
            } else {
                cmd.add("files");
            }
            cmd.add(file.getName());
            Executor executor = new Executor(cmd, file.getParentFile());
            executor.exec();

            /* OUTPUT:
            stdout: //depot_path/name
            stderr: name - no such file(s). 
             */
            return (executor.get_stdout().indexOf("//") != -1);
        } else {
            return false;
        }
    }

    @Override
    boolean isRepositoryFor(File file) {
        return isInP4Depot(file);
    }
}
