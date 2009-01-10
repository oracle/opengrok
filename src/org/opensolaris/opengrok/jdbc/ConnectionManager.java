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
import java.util.concurrent.ConcurrentLinkedQueue;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 * Class that manages the pool of database connections.
 */
public class ConnectionManager {

    private static final String EMBEDDED_DRIVER =
            "org.apache.derby.jdbc.EmbeddedDriver";

    /** The JDBC URL to use when creating new connections. */
    private final String url;

    /** A list of connections not currently in use. */
    private final ConcurrentLinkedQueue<ConnectionResource> connections =
            new ConcurrentLinkedQueue<ConnectionResource>();

    /**
     * Create a new {@code ConnectionManager} instance.
     * @throws ClassNotFoundException if the JDBC driver class cannot be found
     */
    public ConnectionManager() throws ClassNotFoundException {
        Class.forName(EMBEDDED_DRIVER);

        String databasePath =
                RuntimeEnvironment.getInstance().getDataRootPath() +
                File.separator + "cachedb";

        url = "jdbc:derby:" + databasePath + ";create=true";
    }

    /**
     * Open a new connection to the database.
     *
     * @return a {@code Connection} object
     * @throws SQLException if a database error occurs
     */
    Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    /**
     * Get a {@code ConnectionResource} object from the pool, or create a
     * new one if the pool is empty. Callers should make sure that the object
     * is returned to the pool by calling
     * {@link #releaseConnection(ConnectionResource)} after they are finished
     * with it.
     *
     * @return a {@code ConnectionResource} object
     * @throws SQLException if a database error occurs
     */
    public ConnectionResource getConnectionResource() throws SQLException {
        ConnectionResource cr = connections.poll();
        if (cr == null) {
            cr = new ConnectionResource(this);
        }
        return cr;
    }

    /**
     * Return a {@code ConnectionResource} back to the pool.
     *
     * @param cr
     * @throws SQLException
     */
    public void releaseConnection(ConnectionResource cr) throws SQLException {
        cr.rollback();
        connections.offer(cr);
    }
}
