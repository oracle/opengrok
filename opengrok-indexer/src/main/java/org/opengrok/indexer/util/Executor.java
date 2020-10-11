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
 * Copyright (c) 2008, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * Wrapper to Java Process API.
 *
 * @author Emilio Monti - emilmont@gmail.com
 */
public class Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(Executor.class);

    private static final Pattern ARG_WIN_QUOTING = Pattern.compile("[^-:.+=%a-zA-Z0-9_/\\\\]");
    private static final Pattern ARG_UNIX_QUOTING = Pattern.compile("[^-:.+=%a-zA-Z0-9_/]");
    private static final Pattern ARG_GNU_STYLE_EQ = Pattern.compile("^--[-.a-zA-Z0-9_]+=");

    private final List<String> cmdList;
    private final File workingDirectory;
    private byte[] stdout;
    private byte[] stderr;
    private int timeout; // in seconds, 0 means no timeout

    /**
     * Create a new instance of the Executor.
     * @param cmd An array containing the command to execute
     */
    public Executor(String[] cmd) {
        this(Arrays.asList(cmd));
    }

    /**
     * Create a new instance of the Executor.
     * @param cmdList A list containing the command to execute
     */
    public Executor(List<String> cmdList) {
        this(cmdList, null);
    }

    /**
     * Create a new instance of the Executor with default command timeout value.
     * @param cmdList A list containing the command to execute
     * @param workingDirectory The directory the process should have as the
     *                         working directory
     */
    public Executor(List<String> cmdList, File workingDirectory) {
        this.cmdList = cmdList;
        this.workingDirectory = workingDirectory;
        this.timeout = RuntimeEnvironment.getInstance().getIndexerCommandTimeout() * 1000;
    }

    /**
     * Create a new instance of the Executor with specific timeout value.
     * @param cmdList A list containing the command to execute
     * @param workingDirectory The directory the process should have as the
     *                         working directory
     * @param timeout If the command runs longer than the timeout (seconds),
     *                it will be terminated. If the value is 0, no timer
     *                will be set up.
     */
    public Executor(List<String> cmdList, File workingDirectory, int timeout) {
        this.cmdList = cmdList;
        this.workingDirectory = workingDirectory;
        this.timeout = timeout * 1000;
    }

    /**
     * Create a new instance of the Executor with or without timeout.
     * @param cmdList A list containing the command to execute
     * @param workingDirectory The directory the process should have as the
     *                         working directory
     * @param UseTimeout terminate the process after default timeout or not
     */
    public Executor(List<String> cmdList, File workingDirectory, boolean UseTimeout) {
        this(cmdList, workingDirectory);
        if (!UseTimeout) {
            this.timeout = 0;
        }
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
     * Execute the command and collect the output.
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
     * Execute the command and collect the output.
     *
     * @param reportExceptions Should exceptions be added to the log or not
     * @param handler The handler to handle data from standard output
     * @return The exit code of the process
     */
    public int exec(final boolean reportExceptions, StreamHandler handler) {
        int ret = -1;
        stdout = null;
        stderr = null;
        ExecutorProcess ep = null;
        byte[] errBytes = null;
        try {
            Statistics stat = new Statistics();
            ep = startExec(reportExceptions);
            handler.processStream(ep.process.getInputStream());
            ret = ep.process.waitFor();

            stat.report(LOGGER, Level.FINE,
                    String.format("Finished command [%s] in directory %s with exit code %d",
                            ep.cmd_str, ep.dir_str, ret));

            // Wait for the stderr read-out thread to finish the processing and
            // only after that read the data.
            ep.err_thread.join();
            errBytes = ep.err.getBytes();
            stderr = errBytes;
        } catch (IOException e) {
            if (reportExceptions) {
                LOGGER.log(Level.SEVERE, "Failed to read from process: " +
                        cmdList.get(0), e);
            }
            if (ep == null) {
                return ret;
            }
        } catch (InterruptedException e) {
            if (reportExceptions) {
                LOGGER.log(Level.SEVERE, "Waiting for process interrupted: " +
                        cmdList.get(0), e);
            }
        } finally {
            if (ep != null) {
                ret = ep.finish();
            }
        }

        if (ret != 0 && reportExceptions) {
            logErrBytes(ret, ep.cmd_str, ep.dir_str, errBytes);
        }

        return ret;
    }

    /**
     * Starts a command execution, and produces an iterable.
     *
     * @param reportExceptions Should exceptions be added to the log or not
     * @param handler The handler to handle data from standard output
     * @return a defined instance to wrap the execution
     * @throws IOException if an execution cannot be started
     */
    public ObjectCloseableEnumeration startExec(boolean reportExceptions,
            ObjectStreamHandler handler) throws IOException {

        if (handler == null) {
            throw new IllegalArgumentException("handler is null");
        }

        stdout = null;
        stderr = null;
        final ExecutorProcess ep;
        try {
            ep = startExec(reportExceptions);
        } catch (IOException e) {
            if (reportExceptions) {
                LOGGER.log(Level.SEVERE, "Failed to read from process: " + cmdList.get(0), e);
            }
            throw e;
        }

        final String arg0 = cmdList.get(0);
        handler.initializeObjectStream(ep.process.getInputStream());
        Object firstObject = handler.readObject();

        return new ObjectCloseableEnumeration() {

            Object nextObject = firstObject;
            boolean hadProcessError;
            int exitValue = -1;

            @Override
            public int exitValue() {
                return exitValue;
            }

            @Override
            public void close() throws IOException {
                nextObject = null;

                exitValue = ep.finish();
                if (hadProcessError) {
                    throw new IOException("Failed to execute: " + arg0);
                }
            }

            @Override
            public boolean hasMoreElements() {
                return nextObject != null;
            }

            @Override
            public Object nextElement() {
                if (nextObject == null) {
                    throw new NoSuchElementException();
                }
                Object res = nextObject;
                nextObject = null;
                try {
                    nextObject = handler.readObject();
                } catch (IOException e) {
                    hadProcessError = true;
                    if (reportExceptions) {
                        LOGGER.log(Level.SEVERE, "Failed to read from process: " + arg0, e);
                    }
                }

                if (!hadProcessError && nextObject == null) {
                    try {
                        exitValue = ep.process.waitFor();
                        ep.err_thread.join();
                    } catch (InterruptedException e) {
                        hadProcessError = true;
                        if (reportExceptions) {
                            LOGGER.log(Level.SEVERE, "Waiting for process interrupted: " +
                                    arg0, e);
                        }
                    }

                    LOGGER.log(Level.FINE, "Finished command {0} in directory {1}",
                            new Object[] {ep.cmd_str, ep.dir_str});

                    final byte[] errBytes = ep.err.getBytes();
                    stderr = errBytes;
                    if (exitValue != 0 && reportExceptions) {
                        // Limit to avoid flooding the logs.
                        logErrBytes(exitValue, ep.cmd_str, ep.dir_str, errBytes);
                    }
                }
                return res;
            }
        };
    }

    /**
     * Executes the command for its further processing as an active program.
     *
     * @param reportExceptions Should exceptions be added to the log or not
     * @return a defined, started instance
     */
    private ExecutorProcess startExec(final boolean reportExceptions) throws IOException {

        final ExecutorProcess ep = new ExecutorProcess();
        ep.process_builder = new ProcessBuilder(cmdList);
        ep.cmd_str = escapeForShell(ep.process_builder.command(), false,
                PlatformUtils.isWindows());

        if (workingDirectory != null) {
            ep.process_builder.directory(workingDirectory);
            if (ep.process_builder.environment().containsKey("PWD")) {
                ep.process_builder.environment().put("PWD", workingDirectory.getAbsolutePath());
            }
        }

        File cwd = ep.process_builder.directory();
        if (cwd == null) {
            ep.dir_str = System.getProperty("user.dir");
        } else {
            ep.dir_str = cwd.toString();
        }

        String env_str = "";
        if (LOGGER.isLoggable(Level.FINER)) {
            Map<String, String> env_map = ep.process_builder.environment();
            env_str = " with environment: " + env_map.toString();
        }
        LOGGER.log(Level.FINE, "Executing command [{0}] in directory {1}{2}",
                new Object[] {ep.cmd_str, ep.dir_str, env_str});

        final Process proc = ep.process_builder.start();
        ep.process = proc;

        final InputStream errorStream = proc.getErrorStream();
        ep.err_thread = new Thread(() -> {
            try {
                ep.err.processStream(errorStream);
            } catch (IOException ex) {
                if (reportExceptions) {
                    LOGGER.log(Level.SEVERE,
                            "Error while executing command [{0}] in directory {1}",
                            new Object[] {ep.cmd_str, ep.dir_str});
                    LOGGER.log(Level.SEVERE, "Error during process pipe listening", ex);
                }
            }
        });
        ep.err_thread.start();

        final int timeout = this.timeout;
        /*
         * Setup timer so if the process get stuck we can terminate it and
         * make progress instead of hanging the whole operation.
         */
        if (timeout != 0) {
            // invoking the constructor starts the background thread
            ep.timer = new Timer();
            ep.timer.schedule(new TimerTask() {
                @Override public void run() {
                    LOGGER.log(Level.WARNING,
                            String.format("Terminating process of command [%s] in directory %s " +
                            "due to timeout %d seconds", ep.cmd_str, ep.dir_str, timeout / 1000));
                    proc.destroy();
                }
            }, timeout);
        }
        return ep;
    }

    /**
     * Get the output from the process as a string.
     *
     * @return The output from the process
     */
    public String getOutputString() {
        String ret = null;
        if (stdout != null) {
            ret = new String(stdout);
        }

        return ret;
    }

    /**
     * Get a reader to read the output from the process.
     *
     * @return A reader reading the process output
     */
    public Reader getOutputReader() {
        return new InputStreamReader(getOutputStream());
    }

    /**
     * Get an input stream read the output from the process.
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
        String ret = null;
        if (stderr != null) {
            ret = new String(stderr);
        }

        return ret;
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
     * Get an input stream to read the output the process wrote to the error stream.
     *
     * @return An input stream for reading the process error stream
     */
    public InputStream getErrorStream() {
        return new ByteArrayInputStream(stderr);
    }

    /**
     * You should use the StreamHandler interface if you would like to process
     * the output from a process while it is running.
     */
    public interface StreamHandler {

        /**
         * Process the data in the stream. The processStream function is
         * called _once_ during the lifetime of the process, and you should
         * process all of the input you want before returning from the function.
         *
         * @param in The InputStream containing the data
         * @throws java.io.IOException if any read error
         */
        void processStream(InputStream in) throws IOException;
    }

    private static class SpoolHandler implements StreamHandler {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        public byte[] getBytes() {
            return bytes.toByteArray();
        }

        @Override
        public void processStream(InputStream input) throws IOException {            
            BufferedInputStream  in = new BufferedInputStream(input);

            byte[] buffer = new byte[8092];
            int len;

            while ((len = in.read(buffer)) != -1) {
                if (len > 0) {
                    bytes.write(buffer, 0, len);
                }
            }
        }
    }

    private static void logErrBytes(int exitValue, String cmdStr, String dirStr, byte[] errBytes) {
        final int MAX_MSG_SZ = 256;

        StringBuilder msg = new StringBuilder().append("Non-zero exit status ").append(exitValue).
                append(" from command ").append(cmdStr).append(" in directory ").append(dirStr);

        if (errBytes != null && errBytes.length > 0) {
            msg.append(": ");
            if (errBytes.length > MAX_MSG_SZ) {
                msg.append(new String(errBytes, 0, MAX_MSG_SZ)).append("...");
            } else {
                msg.append(new String(errBytes));
            }
        }
        LOGGER.log(Level.WARNING, msg.toString());
    }

    public static void registerErrorHandler() {
        UncaughtExceptionHandler dueh =
            Thread.getDefaultUncaughtExceptionHandler();
        if (dueh == null) {
            LOGGER.log(Level.FINE, "Installing default uncaught exception handler");
            Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    LOGGER.log(Level.SEVERE, "Uncaught exception in thread "
                        + t.getName() + " with ID " + t.getId() + ": "
                        + e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Build a string from the specified argv list with optional tab-indenting
     * and line-continuations if {@code multiline} is {@code true}.
     * @param isWindows a value indicating if the platform is Windows so that
     *                  PowerShell escaping is done; else Bourne shell escaping
     *                  is done.
     * @return a defined instance
     */
    public static String escapeForShell(List<String> argv, boolean multiline, boolean isWindows) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < argv.size(); ++i) {
            if (multiline && i > 0) {
                result.append("\t");
            }
            String arg = argv.get(i);
            result.append(isWindows ? maybeEscapeForPowerShell(arg) : maybeEscapeForSh(arg));
            if (i + 1 < argv.size()) {
                if (!multiline) {
                    result.append(" ");
                } else {
                    result.append(isWindows ? " `" : " \\");
                    result.append(System.lineSeparator());
                }
            }
        }
        return result.toString();
    }

    private static String maybeEscapeForSh(String value) {
        Matcher m = ARG_UNIX_QUOTING.matcher(value);
        if (!m.find()) {
            return value;
        }
        m = ARG_GNU_STYLE_EQ.matcher(value);
        if (!m.find()) {
            return "$'" + escapeForSh(value) + "'";
        }
        String following = value.substring(m.end());
        return m.group() + "$'" + escapeForSh(following) + "'";
    }

    private static String escapeForSh(String value) {
        return value.replace("\\", "\\\\").
                replace("'", "\\'").
                replace("\n", "\\n").
                replace("\r", "\\r").
                replace("\f", "\\f").
                replace("\u0011", "\\v").
                replace("\t", "\\t");
    }

    private static String maybeEscapeForPowerShell(String value) {
        Matcher m = ARG_WIN_QUOTING.matcher(value);
        if (!m.find()) {
            return value;
        }
        m = ARG_GNU_STYLE_EQ.matcher(value);
        if (!m.find()) {
            return "\"" + escapeForPowerShell(value) + "\"";
        }
        String following = value.substring(m.end());
        return m.group() + "\"" + escapeForPowerShell(following) + "\"";
    }

    private static String escapeForPowerShell(String value) {
        return value.replace("`", "``").
                replace("\"", "`\"").
                replace("$", "`$").
                replace("\n", "`n").
                replace("\r", "`r").
                replace("\f", "`f").
                replace("\u0011", "`v").
                replace("\t", "`t");
    }

    private static class ExecutorProcess {
        final SpoolHandler err = new SpoolHandler();
        String cmd_str;
        String dir_str;
        ProcessBuilder process_builder;
        Process process;
        Timer timer;
        Thread err_thread;

        int finish() {
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
            if (process != null) {
                try {
                    IOUtils.close(process.getOutputStream());
                    IOUtils.close(process.getInputStream());
                    IOUtils.close(process.getErrorStream());
                    return process.exitValue();
                } catch (IllegalThreadStateException e) {
                    process.destroy();
                }
            }
            return -1;
        }
    }
}
