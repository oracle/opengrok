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
 * Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Class used to specify how to create a {@code PreparedStatement} to be
 * returned from a {@code ConnectionResource}'s statement cache.
 * @see ConnectionResource#getStatement(StatementCreator)
 */
public abstract class StatementCreator {
    /**
     * Method that creates the statement represented by this statement creator.
     * @param conn the connection on which the statement is prepared
     * @return a {@code PreparedStatement} object
     * @throws SQLException if a database error occurs
     */
    abstract PreparedStatement create(Connection conn) throws SQLException;
}
