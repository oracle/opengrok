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
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A {@code StatementCreator} for statements that create {@code ResultSet}s
 * with specified type, concurrency and holdability.
 */
public final class PreparedQuery extends StatementCreator {
    private final String sql;
    private final int resultSetType;
    private final int resultSetConcurrency;
    private final int resultSetHoldability;

    /**
     * Creator for a statement which returns forward-only, read-only,
     * non-holdable result sets.
     *
     * @param sql the SQL text for the statement
     */
    public PreparedQuery(String sql) {
        this(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    }

    /**
     * Creator for a statement which returns non-holdable result sets with
     * the specified type and concurrency.
     *
     * @param sql the SQL text for the statement
     * @param resultSetType the type of the result set
     * @param resultSetConcurrency the concurrency of the result set
     */
    public PreparedQuery(String sql, int resultSetType,
            int resultSetConcurrency) {
        this(sql, resultSetType, resultSetConcurrency,
                ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    /**
     * Creator for a statement which returns result sets with the specified
     * type, concurrency and holdability.
     *
     * @param sql the SQL text for the statement
     * @param resultSetType the type of the result set
     * @param resultSetConcurrency the concurrency of the result set
     * @param resultSetHoldability the holdability of the result set
     */
    public PreparedQuery(String sql, int resultSetType,
            int resultSetConcurrency, int resultSetHoldability) {
        this.sql = sql;
        this.resultSetType = resultSetType;
        this.resultSetConcurrency = resultSetConcurrency;
        this.resultSetHoldability = resultSetHoldability;
    }

    @Override
    PreparedStatement create(Connection conn) throws SQLException {
        return conn.prepareStatement(
                sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }
}
