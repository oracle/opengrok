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

package org.opensolaris.opengrok.jdbc;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

public class ConnectionManager {

    private static final String EMBEDDED_DRIVER =
            "org.apache.derby.jdbc.EmbeddedDriver";

    private final String url;

    public ConnectionManager() throws ClassNotFoundException {
        Class.forName(EMBEDDED_DRIVER);

        String databasePath =
                RuntimeEnvironment.getInstance().getDataRootPath() +
                File.separator + "cachedb";

        url = "jdbc:derby:" + databasePath + ";create=true";
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public void releaseConnection(Connection conn) throws SQLException {
        conn.rollback();
        conn.close();
    }
}
