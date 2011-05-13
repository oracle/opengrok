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
 * Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.util.IOUtils;

/**
 * Access to a Monotone repository.
 *
 * @author Trond Norbye
 */
public class MonotoneRepository extends Repository {

    private static final long serialVersionUID = 1L;
    /** The property name used to obtain the client command for this repository. */
    public static final String CMD_PROPERTY_KEY =
        "org.opensolaris.opengrok.history.Monotone";
    /** The command to use to access the repository if none was given explicitly */
    public static final String CMD_FALLBACK = "mnt";

    public MonotoneRepository() {
        type = "Monotone";
        datePattern = "yyyy-MM-dd'T'hh:mm:ss";
    }

    @Override
    public InputStream getHistoryGet(String parent, String basename, String rev)
    {
        InputStream ret = null;

        File directory = new File(directoryName);

        Process process = null;
        InputStream in = null;
        String revision = rev;

        try {
            String filename = (new File(parent, basename)).getCanonicalPath()
                .substring(directoryName.length() + 1);
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String argv[] = { cmd, "cat", "-r", revision, filename};
            process = Runtime.getRuntime().exec(argv, null, directory);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[32 * 1024];
            in = process.getInputStream();
            int len;

            while ((len = in.read(buffer)) != -1) {
                if (len > 0) {
                    out.write(buffer, 0, len);
                }
            }

            ret = new BufferedInputStream(new ByteArrayInputStream(out.toByteArray()));
        } catch (Exception exp) {
            OpenGrokLogger.getLogger().log(Level.SEVERE,
                "Failed to get history: " + exp.getClass().toString());
        } finally {
            IOUtils.close(in);
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

    Executor getHistoryLogExecutor(File file, String changeset)
        throws IOException {
        String abs = file.getCanonicalPath();
        String filename = "";
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        }

        List<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(this.cmd);
        cmd.add("log");

        if (changeset != null) {
            cmd.add("--to");
            cmd.add(changeset);
        }

        cmd.add("--no-graph");
        cmd.add("--no-merges");
        cmd.add(filename);

        return new Executor(cmd, new File(directoryName));
    }
    /** Pattern used to extract author/revision from hg annotate. */
    private final static Pattern ANNOTATION_PATTERN =
            Pattern.compile("^(\\w+)\\p{Punct}\\p{Punct} by (\\S+)");

    /**
     * Annotate the specified file/revision.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation
     */
    @Override
    public Annotation annotate(File file, String revision) throws IOException {
        ArrayList<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(this.cmd);
        cmd.add("annotate");
        cmd.add("--reallyquiet");
        if (revision != null) {
            cmd.add("-r");
            cmd.add(revision);
        }
        cmd.add(file.getName());
        File directory = new File(directoryName);

        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }

        BufferedReader in = null;
        Annotation ret = null;
        try {
            in = new BufferedReader(executor.getOutputReader());
            ret = new Annotation(file.getName());
            String line;
            int lineno = 0;
            String author = null;
            String rev = null;
            while ((line = in.readLine()) != null) {
                ++lineno;
                Matcher matcher = ANNOTATION_PATTERN.matcher(line);
                if (matcher.find()) {
                    rev = matcher.group(1);
                    author = matcher.group(2);
                    ret.addLine(rev, author, true);
                } else {
                    ret.addLine(rev, author, true);
                }
            }
        } finally {
            IOUtils.close(in);
        }
        return ret;
    }

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
    }

    @Override
    public void update() throws IOException {
        File directory = new File(directoryName);
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);

        List<String> cmd = new ArrayList<String>();
        cmd.add(this.cmd);
        cmd.add("pull");
        cmd.add("--reallyquiet");
        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }

        cmd.clear();
        cmd.add(this.cmd);
        cmd.add("update");
        cmd.add("--reallyquiet");
        executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }
    }

    @Override
    public boolean fileHasHistory(File file) {
        return true;
    }

    @Override
    boolean isRepositoryFor(File file) {
        File f = new File(file, "_MTN");
        return f.exists() && f.isDirectory();
    }

    @Override
    public boolean isWorking() {
        if (working == null) {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            working = checkCmd(new String[]{ cmd, "--help"});
        }
        return working.booleanValue();
    }

    @Override
    boolean hasHistoryForDirectories() {
        return true;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return getHistory(file, null);
    }

    @Override
    History getHistory(File file, String sinceRevision)
            throws HistoryException {
        return new MonotoneHistoryParser(this).parse(file, sinceRevision);
    }
}
