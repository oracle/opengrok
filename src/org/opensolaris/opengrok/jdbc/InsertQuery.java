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
import java.sql.Statement;

/**
 * A {@code StatementCreator} class that creates insert statements which
 * return generated keys.
 */
public final class InsertQuery extends StatementCreator {

    /** The SQL text for the query. */
    private final String sql;

    /**
     * Create an {@code InsertQuery} instance.
     * @param sql the SQL text
     */
    public InsertQuery(String sql) {
        this.sql = sql;
    }

    /**
     * Create a prepared statement with {@code RETURN_GENERATED_KEYS}.
     */
    @Override
    PreparedStatement create(Connection conn) throws SQLException {
        return conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    }
}
