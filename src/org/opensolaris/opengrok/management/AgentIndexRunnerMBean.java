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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.management;

/**
 * @author Jan S Berg
 */
public interface AgentIndexRunnerMBean {

    public void enable();

    public void disable();

    /**
     * Last index time in System.currentmillis
     * of successful index update
     * @return long when last time the indexing finished
     */
    public long lastIndexTimeFinished();

    /**
     * Last index time in System.currentmillis
     * @return long when last time indexing started
     */
    public long lastIndexTimeStarted();

    /**
     * Last index time usage for successful indexing (no exceptions)
     * @return long how long the last index lasted
     */
    public long lastIndexTimeUsed();

    /**
     * Start indexing outside the timer
     * @param waitForFinished wait for it to finish or just return when started
     */
    public void index(boolean waitForFinished);

    /**
     * Get Exception from last run
     * @return Exception if any or null if no Exceptions.
     */
    public Exception getExceptions();
}
