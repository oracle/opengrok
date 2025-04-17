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
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ThreadUtil {
    private ThreadUtil() {
        // private to enforce static
    }

    /**
     * Retrieve thread ID, preferring the non-deprecated <code>threadId</code> method.
     * This can be replaced with direct call after target Java version is switched to Java 21 or higher.
     * @param thread thread object
     * @return thread id
     */
    public static long getThreadId(Thread thread) {
        Class<? extends Thread> clazz = thread.getClass();
        Method method;
        try {
            method = clazz.getMethod("threadId");
        } catch (NoSuchMethodException e) {
            try {
                method = clazz.getMethod("getId");
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException(ex);
            }
        }
        try {
            return (long) method.invoke(thread);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
