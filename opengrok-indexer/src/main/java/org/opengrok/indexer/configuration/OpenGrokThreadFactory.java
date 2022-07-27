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
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.configuration;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * ThreadFactory to be used throughout OpenGrok to make sure all threads have common prefix.
 */
public class OpenGrokThreadFactory implements ThreadFactory {
    private final String threadPrefix;

    public OpenGrokThreadFactory(String name) {
        if (!name.endsWith("-")) {
            threadPrefix = name + "-";
        } else {
            threadPrefix = name;
        }
    }

    @Override
    public Thread newThread(@NotNull Runnable runnable) {
        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setName("OpenGrok-" + threadPrefix + thread.getId());
        return thread;
    }
}
