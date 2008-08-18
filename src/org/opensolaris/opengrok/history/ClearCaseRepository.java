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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;

/**
 * Access to a ClearCase repository.
 *
 */
public class ClearCaseRepository extends Repository {

    private boolean verbose;

    /**
     * Creates a new instance of ClearCaseRepository
     */
    public ClearCaseRepository() {
    }

    /**
     * Get the name of the ClearCase command that should be used
     * @return the name of the cleartool command in use
     */
    private String getCommand() {
        return System.getProperty("org.opensolaris.opengrok.history.ClearCase", "cleartool");
    }

    /**
     * Use verbose log messages, or just the summary
     * @return true if verbose log messages are used for this repository
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Specify if verbose log messages or just the summary should be used
     * @param verbose set to true if verbose messages should be used
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    Process getHistoryLogProcess(File file) throws IOException {
        String abs = file.getAbsolutePath();
        String filename = "";
        String directoryName = getDirectoryName();
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        }

        ArrayList<String> argv = new ArrayList<String>();
        argv.add(getCommand());
        argv.add("lshistory");
        if (file.isDirectory()) {
            argv.add("-dir");
        }
        argv.add("-fmt");
        argv.add("%e\n%Nd\n%Fu (%u)\n%Vn\n%Nc\n.\n");
        argv.add(filename);

        ProcessBuilder pb = new ProcessBuilder(argv);
        File directory = new File(getDirectoryName());
        pb.directory(directory);
        return pb.start();
    }

    public InputStream getHistoryGet(String parent, String basename, String rev) {
        InputStream ret = null;

        String directoryName = getDirectoryName();
        File directory = new File(directoryName);

        String filename = (new File(parent, basename)).getAbsolutePath().substring(directoryName.length() + 1);
        Process process = null;
        try {
            final File tmp = File.createTempFile("opengrok", "tmp");
            String tmpName = tmp.getAbsolutePath();

            // cleartool can't get to a previously existing file
            if (tmp.exists()) {
                if (!tmp.delete()) {
                    OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to remove temporary file used by history cache");
                }
            }

            String decorated = filename + "@@" + rev;
            String argv[] = {getCommand(), "get", "-to", tmpName, decorated};
            process = Runtime.getRuntime().exec(argv, null, directory);

            drainStream(process.getInputStream());

            if(waitFor(process) != 0) {
                return null;
            }

            ret = new BufferedInputStream(new FileInputStream(tmp) {

                public void close() throws IOException {
                    super.close();
                    // delete the temporary file on close
                    if (!tmp.delete()) {
                        // failed, lets do the next best thing then ..
                        // delete it on JVM exit
                        tmp.deleteOnExit();
                    }
                }
            });
        } catch (Exception exp) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to get history: " + exp.getClass().toString(), exp);
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

        return ret;
    }

    /**
     * Drain all data from a stream and close it.
     * @param in the stream to drain
     * @throws IOException if an I/O error occurs
     */
    private static void drainStream(InputStream in) throws IOException {
        while (true) {
            long skipped = in.skip(32768L);
            if (skipped == 0) {
                // No bytes skipped, check if we've reached EOF with read()
                if (in.read() == -1) {
                    break;
                }
            }
        }
        in.close();
    }

    public Class<? extends HistoryParser> getHistoryParser() {
        return ClearCaseHistoryParser.class;
    }

    public Class<? extends HistoryParser> getDirectoryHistoryParser() {
        return ClearCaseHistoryParser.class;
    }

    /**
     * Annotate the specified file/revision.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation
     */
    public Annotation annotate(File file, String revision) throws Exception {
        ArrayList<String> argv = new ArrayList<String>();

        argv.add(getCommand());
        argv.add("annotate");
        argv.add("-nheader");
        argv.add("-out");
        argv.add("-");
        argv.add("-f");
        argv.add("-fmt");
        argv.add("%u|%Vn|");

        if (revision != null) {
            argv.add(revision);
        }
        argv.add(file.getName());
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(file.getParentFile());
        Process process = null;
        BufferedReader in = null;
        try {
            process = pb.start();
            in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            Annotation a = new Annotation(file.getName());
            String line;
            int lineno = 0;
            while ((line = in.readLine()) != null) {
                ++lineno;
                String parts[] = line.split("\\|");
                String aAuthor = parts[0];
                String aRevision = parts[1];
                aRevision = aRevision.replace('\\', '/');

                a.addLine(aRevision, aAuthor, true);
            }
            return a;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException exp) {
                    // ignore
                }
            }

            if (process != null) {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException e) {
                    process.destroy();
                }
            }
        }
    }

    public boolean fileHasAnnotation(File file) {
        return true;
    }

    public boolean isCacheable() {
        return true;
    }

    private int waitFor(Process process) {

        do {
            try {
                return process.waitFor();
            } catch (InterruptedException exp) {
            }
        } while (true);
    }

    @SuppressWarnings("PMD.EmptyWhileStmt")
    public void update() throws Exception {
        Process process = null;
        BufferedReader in = null;
        try {
            File directory = new File(getDirectoryName());

            // Check if this is a snapshot view
            String[] argv = {getCommand(), "catcs"};
            process = Runtime.getRuntime().exec(argv, null, directory);
            in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            boolean snapshot = false;
            String line;
            while (!snapshot && (line = in.readLine()) != null) {
                snapshot = line.startsWith("load");
            }
            if (waitFor(process) != 0) {
                return;
            }
            in.close();
            in = null; // To avoid double close in finally clause
            if (snapshot) {
                // It is a snapshot view, we need to update it manually
                argv = new String[]{getCommand(), "update", "-overwrite", "-f"};
                process = Runtime.getRuntime().exec(argv, null, directory);
                in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                // consume output
                while ((line = in.readLine()) != null) {
                    // do nothing
                }

                if (waitFor(process) != 0) {
                    return;
                }
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (process != null) {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException e) {
                    process.destroy();
                }
            }
        }
    }

    public boolean fileHasHistory(File file) {
        // Todo: is there a cheap test for whether ClearCase has history
        // available for a file?
        // Otherwise, this is harmless, since ClearCase's commands will just
        // print nothing if there is no history.
        return true;
    }

    @Override
    boolean isRepositoryFor( File file) {
        // if the parent contains a file named "view.dat" or
        // the parent is named "vobs"
        File f = new File(file, "view.dat");
        if (f.exists() && f.isDirectory()) {
            return true;
        } else {
            return file.isDirectory() && file.getName().equalsIgnoreCase("vobs");
        }
    }
}
