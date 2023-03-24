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
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Progress reporting via logging. The idea is that for anything that has a set of items
 * to go through, it will ping an instance of this class for each item completed.
 * This class will then log based on the number of pings. The bigger the progress,
 * the higher log level ({@link Level} value) will be used.
 */
public class Progress implements AutoCloseable {
    private final Logger logger;
    private final Long totalCount;
    private final String suffix;

    private final AtomicLong currentCount = new AtomicLong();
    private final Map<Level, Integer> levelCount;
    private Thread loggerThread = null;
    private volatile boolean run;

    private final Object sync = new Object();

    /**
     * @param logger logger instance
     * @param suffix string suffix to identify the operation
     */
    public Progress(Logger logger, String suffix) {
        this(logger, suffix, -1);
    }

    /**
     * @param logger logger instance
     * @param suffix string suffix to identify the operation
     * @param totalCount total count
     */
    public Progress(Logger logger, String suffix, long totalCount) {
        this.logger = logger;
        this.suffix = suffix;

        if (totalCount < 0) {
            this.totalCount = null;
        } else {
            this.totalCount = totalCount;
        }

        levelCount = Map.of(Level.INFO, 100,
                Level.FINE, 50,
                Level.FINER, 10,
                Level.FINEST, 1);
        
        // Assuming printProgress configuration setting cannot be changed on the fly.
        if (RuntimeEnvironment.getInstance().isPrintProgress()) {
            spawnLogThread();
        }
    }

    private void spawnLogThread() {
        // spawn a logger thread.
        run = true;
        loggerThread = new Thread(this::logLoop,
                "progress-thread-" + suffix.replaceAll(" ", "_"));
        loggerThread.start();
    }

    // for testing
    Thread getLoggerThread() {
        return loggerThread;
    }

    /**
     * Increment counter. The actual logging will be done eventually.
     */
    public void increment() {
        this.currentCount.incrementAndGet();

        if (loggerThread != null) {
            // nag the thread.
            synchronized (sync) {
                sync.notifyAll();
            }
        }
    }

    private void logLoop() {
        long cachedCount = 0;
        Map<Level, Long> lastLoggedChunk = new HashMap<>();

        while (true) {
            long currentCount = this.currentCount.get();
            Level currentLevel = Level.FINEST;

            // Do not log if there was no progress.
            if (cachedCount < currentCount) {
                if (currentCount <= 1 || (totalCount != null && currentCount == totalCount)) {
                    currentLevel = Level.INFO;
                } else {
                    if (lastLoggedChunk.getOrDefault(Level.INFO, -1L) <
                            currentCount / levelCount.get(Level.INFO)) {
                        currentLevel = Level.INFO;
                    } else if (lastLoggedChunk.getOrDefault(Level.FINE, -1L) <
                            currentCount / levelCount.get(Level.FINE)) {
                        currentLevel = Level.FINE;
                    } else if (lastLoggedChunk.getOrDefault(Level.FINER, -1L) <
                            currentCount / levelCount.get(Level.FINER)) {
                        currentLevel = Level.FINER;
                    }
                }

                logIt(lastLoggedChunk, currentCount, currentLevel);
            }

            if (!run) {
                return;
            }

            cachedCount = currentCount;

            // wait for event
            try {
                synchronized (sync) {
                    if (!run) {
                        // Loop once more to do the final logging.
                        continue;
                    }
                    sync.wait();
                }
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, "logger thread interrupted");
            }
        }
    }

    private void logIt(Map<Level, Long> lastLoggedChunk, long currentCount, Level currentLevel) {
        if (logger.isLoggable(currentLevel)) {
            lastLoggedChunk.put(currentLevel, currentCount / levelCount.get(currentLevel));
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Progress: ");
            stringBuilder.append(currentCount);
            stringBuilder.append(" ");
            if (totalCount != null) {
                stringBuilder.append("(");
                stringBuilder.append(String.format("%.2f", currentCount * 100.0f / totalCount));
                stringBuilder.append("%) ");
            }
            stringBuilder.append(suffix);
            logger.log(currentLevel, stringBuilder.toString());
        }
    }

    @Override
    public void close() {
        if (loggerThread == null) {
            return;
        }

        try {
            run = false;
            synchronized (sync) {
                sync.notifyAll();
            }
            loggerThread.join();
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "logger thread interrupted");
        }
    }
}
