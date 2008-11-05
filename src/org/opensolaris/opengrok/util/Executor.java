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
import java.util.ArrayList;
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

    /**
     * Create a new instance of the Executor.
     * @param cmd An array containing the command to execute
     */
    public Executor(String[] cmd) {
        cmdList = new ArrayList<String>();
        for (String s : cmd) {
            cmdList.add(s);
        }
    }

    /**
     * Create a new instance of the Executor.
     * @param cmdList A list containing the command to execute
     */
    public Executor(List<String> cmdList) {
        this(cmdList, null);
    }

    /**
     * Create a new instance of the Executor
     * @param cmdList A list containing the command to execute
     * @param workingDirectory The directory the process should have as the
     *                         working directory
     */
    public Executor(List<String> cmdList, File workingDirectory) {
        this.cmdList = cmdList;
        this.workingDirectory = workingDirectory;
    }

    /**
     * Execute the command and collect the output. All exceptions will be
     * logged.
     *
     * @return The exit code of the process
     */
    public int exec() {
        return exec(true);
    }

    /**
     * Execute the command and collect the output
     * 
     * @param reportExceptions Should exceptions be added to the log or not
     * @return The exit code of the process
     */
    public int exec(boolean reportExceptions) {
        SpoolHandler spoolOut = new SpoolHandler();
        int ret = exec(reportExceptions, spoolOut);
        stdout = spoolOut.getBytes();
        return ret;
    }

    /**
     * Execute the command and collect the output
     * 
     * @param reportExceptions Should exceptions be added to the log or not
     * @param handler The handler to handle data from standard output
     * @return The exit code of the process
     */
    public int exec(final boolean reportExceptions, StreamHandler handler) {
        int ret = -1;

        ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory);
            if (processBuilder.environment().containsKey("PWD")) {
                processBuilder.environment().put("PWD", workingDirectory.getAbsolutePath());
            }
        }

        Process process = null;
        try {
            process = processBuilder.start();

            final InputStream errorStream = process.getErrorStream();
            final SpoolHandler err = new SpoolHandler();
            Thread thread = new Thread(new Runnable() {

                public void run() {
                    try {
                        err.processStream(errorStream);
                    } catch (IOException ex) {
                        if (reportExceptions) {
                            OpenGrokLogger.getLogger().log(Level.SEVERE,
                                    "Error during process pipe listening", ex);
                        }
                    }
                }
            });
            thread.start();

            handler.processStream(process.getInputStream());

            ret = process.waitFor();
            process = null;
            thread.join();
            stderr = err.getBytes();
        } catch (IOException e) {
            if (reportExceptions) {
                OpenGrokLogger.getLogger().log(Level.SEVERE,
                        "Failed to read from process: " + cmdList.get(0), e);
            }
        } catch (InterruptedException e) {
            if (reportExceptions) {
                OpenGrokLogger.getLogger().log(Level.SEVERE,
                        "Waiting for process interrupted: " + cmdList.get(0), e);
            }
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

    /**
     * Get the output from the process as a string.
     * 
     * @return The output from the process
     */
    public String getOutputString() {
        return new String(stdout);
    }

    /**
     * Get a reader to read the output from the process
     * 
     * @return A reader reading the process output
     */
    public Reader getOutputReader() {
        return new InputStreamReader(getOutputStream());
    }

    /**
     * Get an input stream read the output from the process
     * 
     * @return A reader reading the process output
     */
    public InputStream getOutputStream() {
        return new ByteArrayInputStream(stdout);
    }

    /**
     * Get the output from the process written to the error stream as a string.
     * 
     * @return The error output from the process
     */
    public String getErrorString() {
        return new String(stderr);
    }

    /**
     * Get a reader to read the output the process wrote to the error stream.
     * 
     * @return A reader reading the process error stream
     */
    public Reader getErrorReader() {
        return new InputStreamReader(getErrorStream());
    }

    /**
     * Get an inputstreamto read the output the process wrote to the error stream.
     * 
     * @return An inputstream for reading the process error stream
     */
    public InputStream getErrorStream() {
        return new ByteArrayInputStream(stderr);
    }

    /**
     * You should use the StreamHandler interface if you would like to process
     * the output from a process while it is running
     */
    public static interface StreamHandler {

        /**
         * Process the data in the stream. The processStream function is
         * called _once_ during the lifetime of the process, and you should
         * process all of the input you want before returning from the function.
         * 
         * @param in The InputStream containing the data
         * @throws java.io.IOException
         */
        public void processStream(InputStream in) throws IOException;
    }

    private static class SpoolHandler implements StreamHandler {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        public byte[] getBytes() {
            return bytes.toByteArray();
        }

        public void processStream(InputStream in) throws IOException {
            byte[] buffer = new byte[8092];
            int len;

            while ((len = in.read(buffer)) != -1) {
                if (len > 0) {
                    bytes.write(buffer, 0, len);
                }
            }
        }
    }
}
