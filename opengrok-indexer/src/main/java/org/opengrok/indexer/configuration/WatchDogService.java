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
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.configuration;

import org.opengrok.indexer.logger.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class WatchDogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchDogService.class);

    private Thread watchDogThread;
    private WatchService watchDogWatcher;
    public static final int THREAD_SLEEP_TIME = 2000;

    WatchDogService() {

    }

    /**
     * Starts a watch dog service for a directory. It automatically reloads the
     * AuthorizationFramework if there was a change in <b>real-time</b>.
     * Suitable for plugin development.
     *
     * You can control start of this service by a configuration parameter
     * {@link Configuration#authorizationWatchdogEnabled}
     *
     * @param directory root directory for plugins
     */
    public void start(File directory) {
        stop();

        if (directory == null || !directory.isDirectory() || !directory.canRead()) {
            LOGGER.log(Level.INFO, "Watch dog cannot be started - invalid directory: {0}", directory);
            return;
        }
        LOGGER.log(Level.INFO, "Starting watchdog in: {0}", directory);
        watchDogThread = new Thread(() -> {
            try {
                watchDogWatcher = FileSystems.getDefault().newWatchService();
                Path dir = Paths.get(directory.getAbsolutePath());

                Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                        // attach monitor
                        LOGGER.log(Level.FINEST, "Watchdog registering {0}", d);
                        d.register(watchDogWatcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                        return CONTINUE;
                    }
                });

                LOGGER.log(Level.INFO, "Watch dog started {0}", directory);
                while (!Thread.currentThread().isInterrupted()) {
                    final WatchKey key;
                    try {
                        key = watchDogWatcher.take();
                    } catch (ClosedWatchServiceException x) {
                        break;
                    }
                    boolean reload = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        final WatchEvent.Kind<?> kind = event.kind();

                        if (kind == ENTRY_CREATE || kind == ENTRY_DELETE || kind == ENTRY_MODIFY) {
                            reload = true;
                        }
                    }
                    if (reload) {
                        Thread.sleep(THREAD_SLEEP_TIME); // experimental wait if file is being written right now
                        RuntimeEnvironment.getInstance().getAuthorizationFramework().reload();
                    }
                    if (!key.reset()) {
                        break;
                    }
                }
            } catch (InterruptedException | IOException ex) {
                LOGGER.log(Level.FINEST, "Watchdog finishing (exiting)", ex);
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.FINER, "Watchdog finishing (exiting)");
        }, "watchDogService");
        watchDogThread.start();
    }

    /**
     * Stops the watch dog service.
     */
    public void stop() {
        if (watchDogWatcher != null) {
            try {
                watchDogWatcher.close();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Cannot close WatchDogService: ", ex);
            }
        }
        if (watchDogThread != null) {
            watchDogThread.interrupt();
            try {
                watchDogThread.join();
            } catch (InterruptedException ex) {
                LOGGER.log(Level.WARNING, "Cannot join WatchDogService thread: ", ex);
            }
        }
        LOGGER.log(Level.INFO, "Watchdog stopped");
    }
}
