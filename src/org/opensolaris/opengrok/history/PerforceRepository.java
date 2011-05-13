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
import org.opensolaris.opengrok.util.IOUtils;

/**
 * Access to a Perforce repository
 *
 * @author Emilio Monti - emilmont@gmail.com
 */
public class PerforceRepository extends Repository {

    private static final long serialVersionUID = 1L;
    /** The property name used to obtain the client command for this repository. */
    public static final String CMD_PROPERTY_KEY =
        "org.opensolaris.opengrok.history.Perforce";
    /** The command to use to access the repository if none was given explicitly */
    public static final String CMD_FALLBACK = "p4";

    private final static Pattern annotation_pattern =
        Pattern.compile("^(\\d+): .*");

    public PerforceRepository() {
        type = "Perforce";
    }

    @Override
    public Annotation annotate(File file, String rev) throws IOException {
        Annotation a = new Annotation(file.getName());

        List<HistoryEntry> revisions =
            PerforceHistoryParser.getRevisions(file, rev).getHistoryEntries();
        HashMap<String, String> revAuthor = new HashMap<String, String>();
        for (HistoryEntry entry : revisions) {
            // a.addDesc(entry.getRevision(), entry.getMessage());
            revAuthor.put(entry.getRevision(), entry.getAuthor());
        }

        ArrayList<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(this.cmd);
        cmd.add("annotate");
        cmd.add("-q");
        cmd.add(file.getPath() + ((rev == null) ? "" : "#" + rev));

        Executor executor = new Executor(cmd, file.getParentFile());
        executor.exec();

        BufferedReader reader = new BufferedReader(executor.getOutputReader());
        String line;
        int lineno = 0;
        try {
            while ((line = reader.readLine()) != null) {
                ++lineno;
                Matcher matcher = annotation_pattern.matcher(line);
                if (matcher.find()) {
                    String revision = matcher.group(1);
                    String author = revAuthor.get(revision);
                    a.addLine(revision, author, true);
                } else {
                    OpenGrokLogger.getLogger().log(Level.SEVERE,
                        "Error: did not find annotation in line "
                        + lineno + ": [" + line + "]");
                }
            }
        } catch (IOException e) {
            OpenGrokLogger.getLogger().log(Level.SEVERE,
                    "Error: Could not read annotations for " + file, e);
        }
        IOUtils.close(reader);
        return a;
    }

    @Override
    InputStream getHistoryGet(String parent, String basename, String rev) {
        ArrayList<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(this.cmd);
        cmd.add("print");
        cmd.add("-q");
        cmd.add(basename + ((rev == null) ? "" : "#" + rev));
        Executor executor = new Executor(cmd, new File(parent));
        executor.exec();
        return new ByteArrayInputStream(executor.getOutputString().getBytes());
    }

    @Override
    public void update() throws IOException {
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(this.cmd);
        cmd.add("sync");
        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }
    }

    @Override
    boolean fileHasHistory(File file) {
        return true;
    }

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
    }

    private static PerforceRepository testRepo;

    /**
     * Check if a given file is in the depot
     *
     * @param file The file to test
     * @return true if the given file is in the depot, false otherwise
     */
    public static boolean isInP4Depot(File file) {
        boolean status = false;
        if (testRepo == null) {
            testRepo = new PerforceRepository();
        }
        if (testRepo.isWorking()) {
            ArrayList<String> cmd = new ArrayList<String>();
            String name = file.getName();
            File   dir  = file.getParentFile();
            if (file.isDirectory()) {
                dir = file;
                name = "*";
                cmd.add(testRepo.cmd);
                cmd.add("dirs");
                cmd.add(name);
                Executor executor = new Executor(cmd, dir);
                executor.exec();
            /* OUTPUT:
            stdout: //depot_path/name
            stderr: name - no such file(s).
             */
                status = (executor.getOutputString().indexOf("//") != -1);
            }
            if (!status) {
                cmd.clear();
                cmd.add(testRepo.cmd);
                cmd.add("files");
                cmd.add(name);
                Executor executor = new Executor(cmd, dir);
                executor.exec();
            /* OUTPUT:
            stdout: //depot_path/name
            stderr: name - no such file(s).
             */
                status = (executor.getOutputString().indexOf("//") != -1);
            }
        }
        return status;
    }

    @Override
    boolean isRepositoryFor(File file) {
        return isInP4Depot(file);
    }

    @Override
    public boolean isWorking() {
        if (working == null) {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            working = checkCmd(new String[]{ cmd, "help" });
        }
        return working.booleanValue();
    }

    @Override
    boolean hasHistoryForDirectories() {
        return true;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return new PerforceHistoryParser().parse(file, this);
    }
}
