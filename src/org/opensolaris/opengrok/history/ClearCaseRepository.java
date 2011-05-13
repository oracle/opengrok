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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.util.IOUtils;

/**
 * Access to a ClearCase repository.
 *
 */
public class ClearCaseRepository extends Repository {
    private static final long serialVersionUID = 1L;
    /** The property name used to obtain the client command for this repository. */
    public static final String CMD_PROPERTY_KEY =
        "org.opensolaris.opengrok.history.ClearCase";
    /** The command to use to access the repository if none was given explicitly */
    public static final String CMD_FALLBACK = "cleartool";

    private boolean verbose;

    public ClearCaseRepository() {
        type = "ClearCase";
        datePattern = "yyyyMMdd.HHmmss";
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

   /**
     * Get an executor to be used for retrieving the history log for the
     * named file.
     *
     * @param file The file to retrieve history for
     * @return An Executor ready to be started
     */
    Executor getHistoryLogExecutor(final File file) throws IOException {
        String abs = file.getCanonicalPath();
        String filename = "";
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        }

        List<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(this.cmd);
        cmd.add("lshistory");
        if (file.isDirectory()) {
            cmd.add("-dir");
        }
        cmd.add("-fmt");
        cmd.add("%e\n%Nd\n%Fu (%u)\n%Vn\n%Nc\n.\n");
        cmd.add(filename);

        return new Executor(cmd, new File(getDirectoryName()));
    }

    @Override
    public InputStream getHistoryGet(String parent, String basename, String rev)
    {
        InputStream ret = null;

        File directory = new File(directoryName);

        Process process = null;
        try {
            String filename = (new File(parent, basename)).getCanonicalPath()
                .substring(directoryName.length() + 1);
            final File tmp = File.createTempFile("opengrok", "tmp");
            String tmpName = tmp.getCanonicalPath();

            // cleartool can't get to a previously existing file
            if (tmp.exists() && !tmp.delete()) {
                OpenGrokLogger.getLogger().log(Level.WARNING,
                    "Failed to remove temporary file used by history cache");
            }

            String decorated = filename + "@@" + rev;
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String argv[] = {cmd, "get", "-to", tmpName, decorated};
            process = Runtime.getRuntime().exec(argv, null, directory);

            drainStream(process.getInputStream());

            if(waitFor(process) != 0) {
                return null;
            }

            ret = new BufferedInputStream(new FileInputStream(tmp)) {

                @Override
                public void close() throws IOException {
                    super.close();
                    // delete the temporary file on close
                    if (!tmp.delete()) {
                        // failed, lets do the next best thing then ..
                        // delete it on JVM exit
                        tmp.deleteOnExit();
                    }
                }
            };
        } catch (Exception exp) {
            OpenGrokLogger.getLogger().log(Level.SEVERE,
                "Failed to get history: " + exp.getClass().toString(), exp);
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
            long skipped = 0;
            try  {
                skipped = in.skip(32768L);
            } catch (IOException ioe) {
                // ignored - stream isn't seekable, but skipped variable still
                // has correct value.
                OpenGrokLogger.getLogger().log(Level.FINEST,
                    "Stream not seekable", ioe);
            }
            if (skipped == 0 && in.read() == -1) {
                // No bytes skipped, checked that we've reached EOF with read()
                break;
            }
        }
        IOUtils.close(in);
    }

    /**
     * Annotate the specified file/revision.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation
     */
    @Override
public Annotation annotate(File file, String revision) throws IOException {
        ArrayList<String> argv = new ArrayList<String>();

        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(cmd);
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
            IOUtils.close(in);
            if (process != null) {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException e) {
                    process.destroy();
                }
            }
        }
    }

    @Override
    public boolean fileHasAnnotation(File file) {
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
    @Override
    public void update() throws IOException {
        Process process = null;
        BufferedReader in = null;
        try {
            File directory = new File(getDirectoryName());

            // Check if this is a snapshot view
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String[] argv = {cmd, "catcs"};
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
            IOUtils.close(in);
            in = null; // To avoid double close in finally clause
            if (snapshot) {
                // It is a snapshot view, we need to update it manually
                ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
                argv = new String[]{cmd, "update", "-overwrite", "-f"};
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
            IOUtils.close(in);
            
            if (process != null) {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException e) {
                    process.destroy();
                }
            }
        }
    }

    @Override
    public boolean fileHasHistory(File file) {
        // Todo: is there a cheap test for whether ClearCase has history
        // available for a file?
        // Otherwise, this is harmless, since ClearCase's commands will just
        // print nothing if there is no history.
        return true;
    }

    @Override
    public boolean isWorking() {
        if (working == null) {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            working = checkCmd(new String[]{ cmd, "–version" });
        }
        return working.booleanValue();
    }

    @Override
    boolean isRepositoryFor(File file) {
        // if the parent contains a file named "view.dat" or
        // the parent is named "vobs" or the canonical path
        // is found in "cleartool lsvob -s"
        File f = new File(file, "view.dat");
        if (f.exists()) {
            return true;
        } else if (file.isDirectory() && file.getName().equalsIgnoreCase("vobs")) {
            return true;
        } else if (isWorking()) {
            try {
                String canonicalPath = file.getCanonicalPath();
                for (String vob : getAllVobs()) {
                    if (canonicalPath.equalsIgnoreCase(vob)) {
                        return true;
                    }
                }
            } catch (IOException e) {
                OpenGrokLogger.getLogger().log(Level.WARNING,
                    "Could not get canonical path for \""+file+"\"", e);
            }
        }
        return false;
    }

    private static class VobsHolder {
        public static String[] vobs = runLsvob();
    }

    private static String[] getAllVobs() {
        return VobsHolder.vobs;
    }

    private static ClearCaseRepository testRepo;

    private static String[] runLsvob() {
        if (testRepo == null) {
            testRepo = new ClearCaseRepository();
        }
        if (testRepo.isWorking()) {
            Executor exec = new Executor(new String[] {testRepo.cmd, "lsvob", "-s"});
            int rc;
            if ((rc = exec.exec(true)) == 0) {
                String output = exec.getOutputString();

                if (output == null) {
                    OpenGrokLogger.getLogger().log(Level.SEVERE,
                        "\"cleartool lsvob -s\" output was null");
                    return new String[0];
                }
                String sep = System.getProperty("line.separator");
                String[] vobs = output.split(Pattern.quote(sep));
                OpenGrokLogger.getLogger().log(Level.CONFIG, "Found VOBs: {0}",
                    Arrays.asList(vobs));
                return vobs;
            }
            OpenGrokLogger.getLogger().log(Level.SEVERE,
                "\"cleartool lsvob -s\" returned non-zero status: " + rc);
        }
        return new String[0];
    }

    @Override
    boolean hasHistoryForDirectories() {
        return true;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return new ClearCaseHistoryParser().parse(file, this);
    }
}
