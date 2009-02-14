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

package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.opensolaris.opengrok.jdbc.ConnectionManager;
import org.opensolaris.opengrok.jdbc.ConnectionResource;
import org.opensolaris.opengrok.jdbc.InsertQuery;
import org.opensolaris.opengrok.jdbc.PreparedQuery;

class JDBCHistoryCache implements HistoryCache {

    private static final String SCHEMA = "APP";

    private ConnectionManager connectionManager;

    // Many of the tables contain columns with identical names and types,
    // so there will be duplicate strings. Suppress warning from PMD.
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    private static void initDB(Statement s) throws SQLException {
        // TODO Store a database version which is incremented on each
        // format change. When a version change is detected, drop the database
        // (or possibly in the future: add some upgrade code that makes the
        // necessary changes between versions). For now, just check if the
        // tables exist, and create them if necessary.

        DatabaseMetaData dmd = s.getConnection().getMetaData();

        if (!tableExists(dmd, SCHEMA, "REPOSITORIES")) {
            s.executeUpdate("CREATE TABLE REPOSITORIES (" +
                    "ID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, " +
                    "PATH VARCHAR(32672) UNIQUE NOT NULL)");
        }

        if (!tableExists(dmd, SCHEMA, "FILES")) {
            s.executeUpdate("CREATE TABLE FILES (" +
                    "ID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, " +
                    "PATH VARCHAR(32672) NOT NULL, " +
                    "REPOSITORY INT NOT NULL REFERENCES REPOSITORIES " +
                    "ON DELETE CASCADE, " +
                    "UNIQUE (REPOSITORY, PATH))");
        }

        if (!tableExists(dmd, SCHEMA, "AUTHORS")) {
            s.executeUpdate("CREATE TABLE AUTHORS (" +
                    "ID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, " +
                    "REPOSITORY INT NOT NULL REFERENCES REPOSITORIES " +
                    "ON DELETE CASCADE, " +
                    "NAME VARCHAR(32672) NOT NULL, " +
                    "UNIQUE (REPOSITORY, NAME))");
        }

        if (!tableExists(dmd, SCHEMA, "CHANGESETS")) {
            s.executeUpdate("CREATE TABLE CHANGESETS (" +
                    "ID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, " +
                    "REPOSITORY INT NOT NULL REFERENCES REPOSITORIES " +
                    "ON DELETE CASCADE, " +
                    "REVISION VARCHAR(1024) NOT NULL, " +
                    "AUTHOR INT NOT NULL REFERENCES AUTHORS " +
                    "ON DELETE CASCADE, " +
                    "TIME TIMESTAMP NOT NULL, " +
                    "MESSAGE VARCHAR(32672) NOT NULL, " +
                    "UNIQUE (REPOSITORY, REVISION))");
        }

        if (!tableExists(dmd, SCHEMA, "FILECHANGES")) {
            s.executeUpdate("CREATE TABLE FILECHANGES (" +
                    "ID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, " +
                    "FILE INT NOT NULL REFERENCES FILES " +
                    "ON DELETE CASCADE, " +
                    "CHANGESET INT NOT NULL REFERENCES CHANGESETS " +
                    "ON DELETE CASCADE, " +
                    "UNIQUE (FILE, CHANGESET))");
        }
    }

    private static boolean tableExists(
            DatabaseMetaData dmd, String schema, String table)
            throws SQLException {
        ResultSet rs = dmd.getTables(
                null, schema, table, new String[] {"TABLE"});
        try {
            return rs.next();
        } finally {
            rs.close();
        }
    }

    public void initialize() throws HistoryException {
        try {
            connectionManager = new ConnectionManager();
            final ConnectionResource conn =
                    connectionManager.getConnectionResource();
            try {
                final Statement stmt = conn.createStatement();
                try {
                    initDB(stmt);
                } finally {
                    stmt.close();
                }
                conn.commit();
            } finally {
                connectionManager.releaseConnection(conn);
            }
        } catch (Exception e) {
            throw new HistoryException(e);
        }
    }

    private static final PreparedQuery IS_UP_TO_DATE = new PreparedQuery(
            "SELECT F.ID FROM FILES F, REPOSITORIES R " +
            "WHERE F.REPOSITORY = R.ID AND R.PATH = ? AND " +
            "F.PATH LIKE ? || '/%'");

    // We do check the return value from ResultSet.next(), but PMD doesn't
    // understand it, so suppress the warning.
    @SuppressWarnings("PMD.CheckResultSet")
    public boolean isUpToDate(File file, Repository repository)
            throws HistoryException {
        // TODO Find out how this method is used. Seems like it's only called
        // for the top-level directory for each project.
        assert file.isDirectory();
        try {
            final ConnectionResource conn =
                    connectionManager.getConnectionResource();
            try {
                PreparedStatement ps = conn.getStatement(IS_UP_TO_DATE);
                ps.setString(1, toUnixPath(repository.getDirectoryName()));
                ps.setString(2, getRelativePath(file, repository));
                ResultSet rs = ps.executeQuery();
                try {
                    return rs.next();
                } finally {
                    rs.close();
                }
            } finally {
                connectionManager.releaseConnection(conn);
            }
        } catch (SQLException sqle) {
            throw new HistoryException(sqle);
        }
    }

    /**
     * Get path name with all file separators replaced with '/'.
     */
    private static String toUnixPath(String path) {
        return path.replace(File.separatorChar, '/');
    }

    /**
     * Get path name with all file separators replaced with '/'.
     */
    private static String toUnixPath(File file) throws HistoryException {
        try {
            return toUnixPath(file.getCanonicalPath());
        } catch (IOException ioe) {
            throw new HistoryException(ioe);
        }
    }

    /**
     * Get the path of a file relative to the repository root.
     * @param file the file to get the path for
     * @param repository the repository
     * @return relative path for {@code file} with unix file separators
     */
    private static String getRelativePath(File file, Repository repository)
            throws HistoryException {
        String filePath = toUnixPath(file);
        String reposPath = toUnixPath(repository.getDirectoryName());
        assert filePath.startsWith(reposPath);
        return filePath.substring(reposPath.length());
    }

    private static final PreparedQuery GET_HISTORY = new PreparedQuery(
            "SELECT CS.REVISION, A.NAME, CS.TIME, CS.MESSAGE " +
            "FROM CHANGESETS CS, FILECHANGES FC, REPOSITORIES R, " +
            "FILES F, AUTHORS A WHERE R.PATH = ? AND F.PATH = ? AND " +
            "CS.ID = FC.CHANGESET AND R.ID = CS.REPOSITORY AND " +
            "FC.FILE = F.ID AND A.ID = CS.AUTHOR ORDER BY FC.ID");

    public History get(File file, Repository repository)
            throws HistoryException {
        final String filePath = getRelativePath(file, repository);
        final String reposPath = toUnixPath(repository.getDirectoryName());
        final ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        try {
            final ConnectionResource conn =
                    connectionManager.getConnectionResource();
            try {
                PreparedStatement ps = conn.getStatement(GET_HISTORY);
                ps.setString(1, reposPath);
                ps.setString(2, filePath);
                ResultSet rs = ps.executeQuery();
                try {
                    while (rs.next()) {
                        String revision = rs.getString(1);
                        String author = rs.getString(2);
                        Timestamp time = rs.getTimestamp(3);
                        String message = rs.getString(4);
                        HistoryEntry entry = new HistoryEntry(
                                revision, time, author, message, true);
                        entries.add(entry);
                    }
                } finally {
                    rs.close();
                }
            } finally {
                connectionManager.releaseConnection(conn);
            }
        } catch (SQLException sqle) {
            throw new HistoryException(sqle);
        }

        History history = new History();
        history.setHistoryEntries(entries);
        return history;
    }

    private static PreparedQuery GET_REPOSITORY = new PreparedQuery(
            "SELECT ID FROM REPOSITORIES WHERE PATH = ?");

    private static InsertQuery INSERT_REPOSITORY = new InsertQuery(
            "INSERT INTO REPOSITORIES(PATH) VALUES ?");

    public void store(History history, Repository repository)
            throws HistoryException {
        try {
            final ConnectionResource conn =
                    connectionManager.getConnectionResource();
            try {
                storeHistory(conn, history, repository);
            } finally {
                connectionManager.releaseConnection(conn);
            }
        } catch (SQLException sqle) {
            throw new HistoryException(sqle);
        }
    }

    private static InsertQuery ADD_CHANGESET = new InsertQuery(
            "INSERT INTO CHANGESETS" +
            "(REPOSITORY, REVISION, AUTHOR, TIME, MESSAGE) " +
            "VALUES (?,?,?,?,?)");

    private static PreparedQuery ADD_FILECHANGE = new PreparedQuery(
            "INSERT INTO FILECHANGES(CHANGESET, FILE) VALUES (?,?)");

    private void storeHistory(ConnectionResource conn, History history,
            Repository repository) throws SQLException {

        int reposId = getRepositoryId(conn, repository);
        conn.commit();

        Map<String, Integer> authors = getAuthors(conn, history, reposId);
        conn.commit();

        Map<String, Integer> files = getFiles(conn, history, reposId);
        conn.commit();

        PreparedStatement addChangeset = conn.getStatement(ADD_CHANGESET);
        addChangeset.setInt(1, reposId);
        PreparedStatement addFilechange = conn.getStatement(ADD_FILECHANGE);
        for (HistoryEntry entry : history.getHistoryEntries()) {
            addChangeset.setString(2, entry.getRevision());
            addChangeset.setInt(3, authors.get(entry.getAuthor()));
            addChangeset.setTimestamp(4,
                    new Timestamp(entry.getDate().getTime()));
            addChangeset.setString(5, entry.getMessage());
            addChangeset.executeUpdate();

            int changesetId = getGeneratedIntKey(addChangeset);
            addFilechange.setInt(1, changesetId);
            for (String file : entry.getFiles()) {
                int fileId = files.get(toUnixPath(file));
                addFilechange.setInt(2, fileId);
                addFilechange.executeUpdate();
            }

            conn.commit();
        }
    }

    /**
     * Get the id of a repository in the database. If the repository is not
     * stored in the database, add it and return its id.
     *
     * @param conn the connection to the database
     * @param repository the repository whose id to get
     * @return the id of the repository
     */
    private int getRepositoryId(ConnectionResource conn, Repository repository)
            throws SQLException {
        String reposPath = toUnixPath(repository.getDirectoryName());
        PreparedStatement reposIdPS = conn.getStatement(GET_REPOSITORY);
        reposIdPS.setString(1, reposPath);
        ResultSet reposIdRS = reposIdPS.executeQuery();
        try {
            if (reposIdRS.next()) {
                return reposIdRS.getInt(1);
            }
        } finally {
            reposIdRS.close();
        }

        // Repository is not in the database. Add it.
        PreparedStatement insert =
                conn.getStatement(INSERT_REPOSITORY);
        insert.setString(1, reposPath);
        insert.executeUpdate();
        return getGeneratedIntKey(insert);
    }

    private final static PreparedQuery GET_AUTHORS = new PreparedQuery(
            "SELECT NAME, ID FROM AUTHORS WHERE REPOSITORY = ?");

    private final static InsertQuery ADD_AUTHOR = new InsertQuery(
            "INSERT INTO AUTHORS (REPOSITORY, NAME) VALUES (?,?)");

    /**
     * Get a map from author names to their ids in the database. The authors
     * that are not in the database are added to it.
     *
     * @param conn the connection to the database
     * @param history the history to get the author names from
     * @param reposId the id of the repository
     * @return a map from author names to author ids
     */
    private Map<String, Integer> getAuthors(
            ConnectionResource conn, History history, int reposId)
            throws SQLException {
        HashMap<String, Integer> map = new HashMap<String, Integer>();
        PreparedStatement ps = conn.getStatement(GET_AUTHORS);
        ps.setInt(1, reposId);
        ResultSet rs = ps.executeQuery();
        try {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getInt(2));
            }
        } finally {
            rs.close();
        }

        PreparedStatement insert = conn.getStatement(ADD_AUTHOR);
        insert.setInt(1, reposId);
        for (HistoryEntry entry : history.getHistoryEntries()) {
            String author = entry.getAuthor();
            if (!map.containsKey(author)) {
                insert.setString(2, author);
                insert.executeUpdate();
                int id = getGeneratedIntKey(insert);
                map.put(author, id);
            }
        }

        return map;
    }

    private static PreparedQuery GET_FILES = new PreparedQuery(
            "SELECT PATH, ID FROM FILES WHERE REPOSITORY = ?");

    private static InsertQuery INSERT_FILE = new InsertQuery(
            "INSERT INTO FILES(REPOSITORY, PATH) VALUES (?,?)");

    /**
     * Get a map from file names to their ids in the database. The files
     * that are not in the database are added to it.
     *
     * @param conn the connection to the database
     * @param history the history to get the file names from
     * @param reposId the id of the repository
     * @return a map from file names to file ids
     */
    private Map<String, Integer> getFiles(
            ConnectionResource conn, History history, int reposId)
            throws SQLException {
        Map<String, Integer> map = new HashMap<String, Integer>();
        PreparedStatement ps = conn.getStatement(GET_FILES);
        ps.setInt(1, reposId);
        ResultSet rs = ps.executeQuery();
        try {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getInt(2));
            }
        } finally {
            rs.close();
        }

        PreparedStatement insert = conn.getStatement(INSERT_FILE);
        insert.setInt(1, reposId);
        for (HistoryEntry entry : history.getHistoryEntries()) {
            for (String file : entry.getFiles()) {
                String path = toUnixPath(file);
                if (!map.containsKey(path)) {
                    insert.setString(2, path);
                    insert.executeUpdate();
                    map.put(path, getGeneratedIntKey(insert));
                }
            }
        }

        return map;
    }

    /**
     * Return the integer key generated by the previous execution of a
     * statement. The key should be a single INTEGER, and the statement
     * should insert exactly one row, so there should be only one key.
     * @param stmt a statement that has just inserted a row
     * @return the integer key for the newly inserted row, or {@code null}
     * if there is no key
     */
    private Integer getGeneratedIntKey(Statement stmt) throws SQLException {
        ResultSet keys = stmt.getGeneratedKeys();
        try {
            if (keys.next()) {
                return keys.getInt(1);
            } else {
                return null;
            }
        } finally {
            keys.close();
        }
    }
}
