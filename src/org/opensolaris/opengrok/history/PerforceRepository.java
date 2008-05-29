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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensolaris.opengrok.util.Executor;

/**
 * Access to a Perforce repository
 * 
 * @author Emilio Monti - emilmont@gmail.com
 */
public class PerforceRepository extends ExternalRepository {

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
                    System.err.println("Error: did not find annotation in line " + lineno);
                    System.err.println("[" + line + "]");
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
    boolean supportsAnnotation() {
        return true;
    }
}
