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
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that manages the resources associated with a database connection.
 * This includes a cache of {@code PreparedStatement}s.
 */
public class ConnectionResource {
    /** The connection to the database. */
    private final Connection conn;

    /** Statement cache. */
    private final Map<StatementCreator, PreparedStatement> statements =
            new HashMap<StatementCreator, PreparedStatement>();

    /**
     * Create a new {@code ConnectionResource} instance.
     * @param manager the {@code ConnectionManager} that created this object
     * @throws SQLException if an error occurs when connecting to the database
     */
    ConnectionResource(ConnectionManager manager) throws SQLException {
        conn = manager.openConnection();
        conn.setAutoCommit(false);
    }

    /**
     * Check if the connection is still valid.
     * @return {@code true} if the connection is valid, {@code false} if not
     * @throws SQLException if a database error occurs
     */
    public boolean isValid() throws SQLException {
        // Check whether the connection is valid. Assume that the connection
        // is lost if there's no response after 10 seconds. (Will normally
        // return immediately if the connection is lost, but use a timeout
        // in case of odd network issues.)
        return conn.isValid(10);
    }

    /**
     * Commit the transaction.
     * @throws SQLException if a database error occurs
     */
    public void commit() throws SQLException {
        conn.commit();
    }

    /**
     * Abort the transaction.
     * @throws SQLException if a database error occurs
     */
    public void rollback() throws SQLException {
        conn.rollback();
    }

    /**
     * Get a {@code PreparedStatement} as defined by the specified
     * {@code StatementCreator}. If it is the first time the statement
     * creator is used on this {@code ConnectionResource}, the creator's
     * {@link StatementCreator#create(Connection)} method is called to
     * create a new {@code PreparedStatement}. This statement is cached,
     * so that on subsequent calls with the same statement creator, the same
     * {@code PreparedStatement} will be returned.
     *
     * @param creator object that specifies how to create the statement if
     * necessary
     * @return a {@code PreparedStatement} object
     * @throws SQLException if a database error occurs
     */
    public PreparedStatement getStatement(StatementCreator creator)
            throws SQLException {
        PreparedStatement ps = statements.get(creator);
        if (ps == null) {
            // Should also check isClosed() here, but it's only available in
            // JDK 6, so we can't. Anyway, callers shouldn't close the
            // statements they get from this method.
            ps = creator.create(conn);
            statements.put(creator, ps);
        }
        return ps;
    }

    /**
     * Create a new {@code Statement} object.
     * @return a {@code Statement} object
     * @throws java.sql.SQLException
     */
    public Statement createStatement() throws SQLException {
        return conn.createStatement();
    }

    /**
     * Prepare a statement. This method should only be used to prepare
     * statements that are used infrequently. If a statement is likely to
     * be used frequently, a {@code StatementCreator} object should be
     * created for it, and the method {@link #getStatement(StatementCreator)}
     * should be used instead.
     *
     * @param sql the SQL text to compile
     * @return a prepared statement
     * @throws SQLException if a database error occurs
     */
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return conn.prepareStatement(sql);
    }

    /**
     * Get a meta-data object for the underlying connection.
     *
     * @return a {@code DatabaseMetaData} object
     * @throws SQLException if a database error occurs
     */
    public DatabaseMetaData getMetaData() throws SQLException {
        return conn.getMetaData();
    }
}
