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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
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
    private byte[] stdout;
    private byte[] stderr;

    public Executor(List<String> cmdList) {
        this(cmdList, null);
    }

    public Executor(List<String> cmdList, File workingDirectory) {
        this.cmdList = cmdList;
        this.workingDirectory = workingDirectory;
    }

    public int exec() {
        int ret = -1;

        ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory);
        }

        Spooler spoolOut = new Spooler();
        Spooler spoolErr = new Spooler();
        Process process = null;
        try {
            process = processBuilder.start();
            spoolOut.startListen(process.getInputStream());
            spoolErr.startListen(process.getErrorStream());
            ret = process.waitFor();
            process = null;
            spoolOut.join();
            spoolErr.join();
            stdout = spoolOut.getBytes();
            stderr = spoolErr.getBytes();
        } catch (IOException e) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, 
                    "Failed to read from process: " + cmdList.get(0), e);
        } catch (InterruptedException e) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, 
                    "Waiting for process interrupted: "  + cmdList.get(0), e);
        } finally {
            try {
                if (process != null) {
                    ret = process.exitValue();
                }
            } catch (IllegalThreadStateException e) {
                process.destroy();
            }
        }

        return ret;
    }

    public String getOutputString() {
        return new String(stdout);
    }

    public Reader getOutputReader() {
        return new InputStreamReader(getOutputStream());
    }

    public InputStream getOutputStream() {
        return new ByteArrayInputStream(stdout);
    }

    public String getErrorString() {
        return new String(stderr);
    }

    public Reader getErrorReader() {
        return new InputStreamReader(getErrorStream());
    }

    public InputStream getErrorStream() {
        return new ByteArrayInputStream(stderr);
    }

    private static class Spooler extends Thread {

        private InputStream input;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        public byte[] getBytes() {
            return bytes.toByteArray();
        }

        void startListen(InputStream is) {
            input = is;
            this.start();
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[8092];
                int len;

                while ((len = input.read(buffer)) != -1) {
                    if (len > 0) {
                        bytes.write(buffer, 0, len);
                    }
                }
            } catch (IOException ioe) {
                OpenGrokLogger.getLogger().log(Level.SEVERE, 
                        "Error during process pipe listening", ioe);
            }
        }
    }
}
