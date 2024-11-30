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
package org.opengrok.suggest.util;

import org.jetbrains.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Copy of {@code org.opengrok.indexer.util.Progress}.
 * <p>
 * Progress reporting via logging. The idea is that for anything that has a set of items
 * to go through, it will ping an instance of this class for each item completed.
 * This class will then log based on the number of pings. The bigger the progress,
 * the higher log level ({@link Level} value) will be used. The default base level is {@code Level.INFO}.
 * Regardless of the base level, maximum 4 log levels will be used.
 * </p>
 */
public class Progress implements AutoCloseable {
    private final Logger logger;
    private final Long totalCount;
    private final String suffix;

    private final AtomicLong currentCount = new AtomicLong();
    private final Map<Level, Integer> levelCountMap = new TreeMap<>(Comparator.comparingInt(Level::intValue).reversed());
    private Thread loggerThread = null;
    private volatile boolean run;

    private final Level baseLogLevel;

    private final Object sync = new Object();

    /**
     * @param logger logger instance
     * @param suffix string suffix to identify the operation
     * @param totalCount total count
     * @param logLevel base log level
     * @param isPrintProgress whether to print the progress
     */
    @SuppressWarnings("this-escape")
    public Progress(Logger logger, String suffix, long totalCount, Level logLevel, boolean isPrintProgress) {
        this.logger = logger;
        this.suffix = suffix;
        this.baseLogLevel = logLevel;

        if (totalCount < 0) {
            this.totalCount = null;
        } else {
            this.totalCount = totalCount;
        }

        // Note: Level.CONFIG is missing as it does not make too much sense for progress reporting semantically.
        final List<Level> standardLevels = Arrays.asList(Level.OFF, Level.SEVERE, Level.WARNING, Level.INFO,
                Level.FINE, Level.FINER, Level.FINEST, Level.ALL);
        int[] num = new int[]{100, 50, 10, 1};
        for (int i = standardLevels.indexOf(baseLogLevel), j = 0;
             i < standardLevels.size() && j < num.length; i++, j++) {

            Level level = standardLevels.get(i);
            levelCountMap.put(level, num[j]);
        }

        // Assuming the printProgress configuration setting cannot be changed on the fly.
        if (!baseLogLevel.equals(Level.OFF) && isPrintProgress) {
            spawnLogThread();
        }
    }

    private void spawnLogThread() {
        // spawn a logger thread.
        run = true;
        loggerThread = new Thread(this::logLoop,
                "progress-thread-" + suffix.replace(" ", "_"));
        loggerThread.start();
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
            long longCurrentCount = this.currentCount.get();
            Level currentLevel = Level.FINEST;

            // Do not log if there was no progress.
            if (cachedCount < longCurrentCount) {
                currentLevel = getLevel(lastLoggedChunk, longCurrentCount, currentLevel);
                logIt(lastLoggedChunk, longCurrentCount, currentLevel);
            }

            if (!run) {
                return;
            }

            cachedCount = longCurrentCount;

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

    @VisibleForTesting
    Level getLevel(Map<Level, Long> lastLoggedChunk, long currentCount, Level currentLevel) {
        // The intention is to log the initial and final count at the base log level.
        if (currentCount <= 1 || (totalCount != null && currentCount == totalCount)) {
            currentLevel = baseLogLevel;
        } else {
            // Set the log level based on the "buckets".
            for (var levelCountItem : levelCountMap.entrySet()) {
                if (lastLoggedChunk.getOrDefault(levelCountItem.getKey(), -1L) <
                        currentCount / levelCountItem.getValue()) {
                    currentLevel = levelCountItem.getKey();
                    break;
                }
            }
        }
        return currentLevel;
    }

    private void logIt(Map<Level, Long> lastLoggedChunk, long currentCount, Level currentLevel) {
        if (logger.isLoggable(currentLevel)) {
            lastLoggedChunk.put(currentLevel, currentCount / levelCountMap.get(currentLevel));
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
