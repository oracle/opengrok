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
 * Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.IOUtils;

/**
 * Represents a wrapper to run mandoc binary to format manual page content.
 */
public class MandocRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(
        MandocRunner.class);

    private Process mandoc;
    private OutputStreamWriter mandocIn;
    private BufferedReader mandocOut;
    private Thread errThread;
    private String osOverride = "GENERIC SYSTEM";

    /**
     * Gets the value used for mandoc's setting to "Override the default
     * operating system name". Default is {@code "GENERIC SYSTEM"}.
     * @return a defined value or {@code null}
     */
    public String getOSOverride() {
        return osOverride;
    }

    /**
     * Sets the value used for mandoc's setting to "Override the default
     * operating system name".
     * @param value a defined value or {@code null} to disable the override
     */
    public void setOSOverride( String value ) {
        osOverride = value;
    }

    /**
     * Starts a run of the mandoc binary to receive input from {@link write}.
     * @throws IOException thrown if a read or write to the mandoc process
     * fails.
     * @throws MandocException if no mandoc binary is defined
     */
    public void start() throws IOException, MandocException {

        destroy();

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String binary = env.getMandoc();
        if (binary == null) {
            throw new MandocException("no mandoc binary is defined");
        }

        List<String> command = new ArrayList<>();

        command.add(binary);
        command.add("-c");  // do not use `more`
        command.add("-Kutf-8");  // explicitly set the output encoding
        command.add("-Thtml");  // Produce HTML5, CSS1, and MathML output
        command.add("-Ofragment");  // HTML fragment only

        // Override the default operating system name
        String oo = osOverride;
        if (oo != null) {
            command.add("-I");
            command.add("os=" + oo);
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            StringBuilder sb = new StringBuilder();
            command.forEach((s) -> {
                sb.append(s).append(" ");
            });
            String cmd = sb.toString();
            LOGGER.log(Level.FINER, "Executing mandoc command [{0}]", cmd);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);

        Process starting = processBuilder.start();
        OutputStreamWriter inn = new OutputStreamWriter(
            starting.getOutputStream(), StandardCharsets.UTF_8);
        BufferedReader rdr = new BufferedReader(new InputStreamReader(
            starting.getInputStream(), StandardCharsets.UTF_8));
        InputStream errorStream = starting.getErrorStream();
        mandocIn = inn;
        mandocOut = rdr;
        mandoc = starting;

        errThread = new Thread(() -> {
            // implicitly capture `errorStream' for the InputStreamReader
            try (BufferedReader error = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                String s;
                while ((s = error.readLine()) != null) {
                    LOGGER.log(Level.WARNING, "Error from mandoc: {0}", s);
                }
            } catch (IOException ex) {
                // ignore
            }
        });
        errThread.setDaemon(true);
        errThread.start();
    }

    /**
     * Finishes a run of mandoc for its output.
     * @return the stdout output of the run.
     * @throws IOException thrown if closing the mandoc process fails.
     * @throws MandocException thrown if mandoc exits non-zero.
     */
    public String finish() throws IOException, MandocException {
        if (mandoc == null) {
            return "";
        }

        StringBuilder output = new StringBuilder();

        int rc;
        try {
            String line;
            mandocIn.close();
            while ((line = mandocOut.readLine()) != null) {
                output.append(line);
                output.append('\n');
            }
            mandoc.waitFor(10, TimeUnit.SECONDS);
            rc = mandoc.exitValue();
        } catch (InterruptedException e) {
            return "";
        } finally {
            destroy();
        }

        if (rc != 0) {
            throw new MandocException("exit code " + rc);
        }
        return output.toString();
    }

    /**
     * Writes a character to the input of the run of mandoc.
     * @param s the string to write.
     * @throws IllegalStateException thrown if the runner was not successfully
     * {@link start}ed.
     * @throws IOException thrown if a write to the mandoc process fails.
     */
    public void write(String s) throws IOException {
        if (mandocIn == null) {
            throw new IllegalStateException("start() must succeed first");
        }

        mandocIn.write(s);
    }

    /**
     * Kills the mandoc process if it is running.
     */
    public void destroy() {
        // terminate straightaway any existing run
        if (errThread != null) {
            errThread.interrupt();
            errThread = null;
        }
        if (mandoc != null) {
            Process destroying = mandoc;
            mandoc = null;

            if (mandocIn != null) {
                IOUtils.close(mandocIn);
                mandocIn = null;
            }
            if (mandocOut != null) {
                IOUtils.close(mandocOut);
                mandocOut = null;
            }

            destroying.destroyForcibly();
        }
    }
}
