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

package org.opensolaris.opengrok.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.List;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;

/**
 * Wrapper to Java Process API
 *
 * @author Emilio Monti - emilmont@gmail.com
 */
public class Executor {

    private List<String> cmdList;
    private File workingDirectory;
    private String stdoutString = "";
    private String stderrString = "";

    public Executor(List<String> cmdList) {
        this(cmdList, null);
    }

    public Executor(List<String> cmdList, File workingDirectory) {
        this.cmdList = cmdList;
        this.workingDirectory = workingDirectory;
    }

    public void exec() {
        ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory);
        }

        StringPipe stdout = new StringPipe();
        StringPipe stderr = new StringPipe();
        Process process = null;
        try {
            process = processBuilder.start();
            stdout.startListen(process.getInputStream());
            stderr.startListen(process.getErrorStream());
            process.waitFor();
            stdout.join();
            stderr.join();
            stdoutString = stdout.getString();
            stderrString = stdout.getString();
        } catch (IOException e) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, 
                    "Failed to read from process: " + cmdList.get(0), e);
        } catch (InterruptedException e) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, 
                    "Waiting for process interrupted: "  + cmdList.get(0), e);
        } finally {
            try {
                if (process != null) {
                    process.exitValue();
                }
            } catch (IllegalThreadStateException e) {
                process.destroy();
            }
        }
    }

    public String get_stdout() {
        return stdoutString;
    }

    public BufferedReader get_stdout_reader() {
        return new BufferedReader(new StringReader(stdoutString));
    }

    public String get_stderr() {
        return stderrString;
    }

    public BufferedReader get_stderr_reader() {
        return new BufferedReader(new StringReader(stderrString));
    }

    private static class StringPipe extends Thread {

        private InputStream input = null;
        private String output = null;

        void startListen(InputStream is) {
            input = is;
            this.start();
        }

        @Override
        public void run() {
            try {
                int byteIn;
                StringBuffer sb = new StringBuffer();
                while ((byteIn = input.read()) != -1) {
                    sb.append((char) byteIn);
                }
                output = sb.toString();
            } catch (IOException ioe) {
                OpenGrokLogger.getLogger().log(Level.SEVERE, 
                        "Error during process pipe listening", ioe);
            }
        }

        public String getString() {
            return output;
        }
    }
}
