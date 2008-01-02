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
package org.opensolaris.opengrok.index;

/**
 * The changes in an index database may be monitored through this interface.
 * 
 * @author Trond Norbye
 */
interface IndexChangedListener {
    /**
     * A file is added to the index database
     * @param path The path to the file (absolute from source root)
     * @param analyzer The analyzer being used to analyze the file
     */
    public void fileAdded(String path, String analyzer);
    /**
     * A file is being removed from the index database
     * @param path The path to the file (absolute from source root)
     */
    public void fileRemoved(String path);
}
