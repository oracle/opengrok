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
 * Copyright (c) 2007, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.util;

import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Progress {
    private Logger logger;
    private int totalCount;
    private String prefix;

    private AtomicInteger currentCount = new AtomicInteger();

    /**
     * @param logger logger instance
     * @param prefix string prefix to identify the operation
     * @param totalCount total count
     */
    public Progress(Logger logger, String prefix, int totalCount) {
        this.logger = logger;
        this.prefix = prefix;
        this.totalCount = totalCount;
    }

    /**
     * increment counter and log progress.
     */
    public void incrementAndLog() {
        if (RuntimeEnvironment.getInstance().isPrintProgress()) {
            int currentCount = this.currentCount.incrementAndGet();
            Level currentLevel;
            if (currentCount <= 1 || currentCount >= totalCount ||
                    currentCount % 100 == 0) {
                currentLevel = Level.INFO;
            } else if (currentCount % 50 == 0) {
                currentLevel = Level.FINE;
            } else if (currentCount % 10 == 0) {
                currentLevel = Level.FINER;
            } else {
                currentLevel = Level.FINEST;
            }
            if (logger.isLoggable(currentLevel)) {
                logger.log(currentLevel, "Progress: {0} ({1}%) for {2}",
                        new Object[]{currentCount, currentCount * 100.0f /
                                totalCount, prefix});
            }
        }
    }
}
