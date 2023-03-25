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
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;

public class ProgressTest {
    @BeforeAll
    public static void setup() {
        // needed to spawn logger thread in Progress
        RuntimeEnvironment.getInstance().setPrintProgress(true);
    }

    @Test
    void testShifting() {
        final Logger logger = Mockito.mock(Logger.class);
        try (Progress progress = new Progress(logger, "xxx")) {
            assertNotNull(progress);
        }
    }

    @Test
    public void testProgress() throws InterruptedException {
        final Logger logger = Mockito.mock(Logger.class);
        final int totalCount = 100; // use int because of Mockito.calls()/times() only accepts int
        Thread loggerThread = null;

        Mockito.when(logger.isLoggable(any())).thenReturn(true);
        try (Progress progress = new Progress(logger, "foo", totalCount)) {
            assertNotNull(progress.getLoggerThread());
            loggerThread = progress.getLoggerThread();

            // Progress does not give any indication that the logger thread reached the point when
            // it can accept notifications from increment() so check the Thread state.
            while (loggerThread.getState() != Thread.State.WAITING) {
                System.out.println("Waiting for the logger thread to reach the initial wait()");
                TimeUnit.MILLISECONDS.sleep(10);
            }

            for (int i = 0; i < totalCount; i++) {
                progress.increment();
                // Give the logger thread some time to log.
                while (loggerThread.getState() != Thread.State.WAITING) {
                    TimeUnit.MILLISECONDS.sleep(10);
                }
            }
        }

        System.out.println("Waiting for the logger thread to terminate");
        int i = 0;
        while (i < 3 && loggerThread.getState() != Thread.State.TERMINATED) {
            TimeUnit.MILLISECONDS.sleep(10);
            i++;
        }
        assertSame(loggerThread.getState(), Thread.State.TERMINATED);

        Mockito.verify(logger, times(totalCount)).log(any(), anyString());
        Mockito.verify(logger, atLeast(1)).log(same(Level.INFO), anyString());
        Mockito.verify(logger, atLeast(2)).log(same(Level.FINE), anyString());
        Mockito.verify(logger, atLeast(10)).log(same(Level.FINER), anyString());
        Mockito.verify(logger, atLeast(50)).log(same(Level.FINEST), anyString());
    }

    @Test
    public void testThreads() throws InterruptedException {
        final Logger logger = Mockito.mock(Logger.class);
        final int totalCount = Runtime.getRuntime().availableProcessors();
        List<Future<?>> futures = new ArrayList<>();

        Mockito.when(logger.isLoggable(any())).thenReturn(true);
        ExecutorService executor = Executors.newFixedThreadPool(totalCount);
        System.out.printf("Will run %d threads%n", totalCount);
        try (Progress progress = new Progress(logger, "foo", totalCount)) {
            while (progress.getLoggerThread().getState() != Thread.State.WAITING) {
                System.out.println("Waiting for the logger thread to reach the initial wait()");
                TimeUnit.MILLISECONDS.sleep(10);
            }

            for (int i = 0; i < totalCount; i++) {
                futures.add(executor.submit(progress::increment));
            }

            System.out.println("Waiting for threads to finish");
            futures.forEach(future -> {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        executor.shutdown();
        System.out.println("Verifying");
        Mockito.verify(logger, atLeast(1)).log(any(), anyString());
    }
}
