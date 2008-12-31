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
import java.sql.Connection;
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

class JDBCHistoryCache implements HistoryCache {

    private static final String SCHEMA = "APP";

    private ConnectionManager connectionManager;

    // Many of the tables contain columns with identical names and types,
    // so there will be duplicate strings. Suppress warning from PMD.
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    private static void initDB(Connection c, Statement s) throws SQLException {
        // TODO Store a database version which is incremented on each
        // format change. When a version change is detected, drop the database
        // (or possibly in the future: add some upgrade code that makes the
        // necessary changes between versions). For now, just check if the
        // tables exist, and create them if necessary.

        DatabaseMetaData dmd = c.getMetaData();

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
                    "LAST_MODIFICATION TIMESTAMP NOT NULL, " +
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
            final Connection conn = connectionManager.getConnection();
            try {
                conn.setAutoCommit(false);
                final Statement stmt = conn.createStatement();
                try {
                    initDB(conn, stmt);
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

    // We do check the return value from ResultSet.next(), but PMD doesn't
    // understand it, so suppress the warning.
    @SuppressWarnings("PMD.CheckResultSet")
    public boolean isUpToDate(File file, Repository repository)
            throws HistoryException {
        // TODO Find out how this method is used. Seems like it's only called
        // for the top-level directory for each project.
        assert file.isDirectory();
        try {
            final Connection conn = connectionManager.getConnection();
            try {
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT F.ID FROM FILES F, REPOSITORIES R " +
                        "WHERE F.REPOSITORY = R.ID AND R.PATH = ? AND " +
                        "F.PATH LIKE ? || '/%'");
                try {
                    ps.setString(1, toUnixPath(repository.getDirectoryName()));
                    ps.setString(2, getRelativePath(file, repository));
                    ResultSet rs = ps.executeQuery();
                    try {
                        return rs.next();
                    } finally {
                        rs.close();
                    }
                } finally {
                    ps.close();
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

    public History get(File file, Repository repository)
            throws HistoryException {
        final String filePath = getRelativePath(file, repository);
        final String reposPath = toUnixPath(repository.getDirectoryName());
        final ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        try {
            final Connection conn = connectionManager.getConnection();
            try {
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT CS.REVISION, A.NAME, CS.TIME, CS.MESSAGE " +
                        "FROM CHANGESETS CS, FILECHANGES FC, REPOSITORIES R, " +
                        "FILES F, AUTHORS A " +
                        "WHERE R.PATH = ? AND F.PATH = ? AND " +
                        "CS.ID = FC.CHANGESET AND R.ID = CS.REPOSITORY AND " +
                        "FC.FILE = F.ID AND A.ID = CS.AUTHOR " +
                        "ORDER BY FC.ID");
                try {
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
                    ps.close();
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

    // Assume that this file is never called concurrently from different
    // threads on files in the same repository.
    public void store(History history, File file, Repository repository)
            throws HistoryException {
        try {
            final String filePath = getRelativePath(file, repository);
            final String reposPath = toUnixPath(repository.getDirectoryName());
            final Connection conn = connectionManager.getConnection();
            try {
                conn.setAutoCommit(false);

                Integer reposId = null;
                PreparedStatement reposIdPS = conn.prepareStatement(
                        "SELECT ID FROM REPOSITORIES WHERE PATH = ?");
                try {
                    reposIdPS.setString(1, reposPath);
                    ResultSet reposIdRS = reposIdPS.executeQuery();
                    try {
                        if (reposIdRS.next()) {
                            reposId = reposIdRS.getInt(1);
                        }
                    } finally {
                        reposIdRS.close();
                    }
                } finally {
                    reposIdPS.close();
                }

                if (reposId == null) {
                    PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO REPOSITORIES(PATH) VALUES ?",
                            Statement.RETURN_GENERATED_KEYS);
                    insert.setString(1, reposPath);
                    insert.executeUpdate();
                    reposId = getGeneratedIntKey(insert);
                    insert.close();
                }

                assert reposId != null;

                PreparedStatement fileInfoPS = conn.prepareStatement(
                        "SELECT ID, LAST_MODIFICATION FROM FILES " +
                        "WHERE REPOSITORY = ? AND PATH = ?",
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_UPDATABLE);

                fileInfoPS.setInt(1, reposId);
                fileInfoPS.setString(2, filePath);
                ResultSet fileInfoRS = fileInfoPS.executeQuery();
                Integer fileId = null;
                try {
                    if (fileInfoRS.next()) {
                        fileId = fileInfoRS.getInt(1);
                        long lastMod = fileInfoRS.getTimestamp(2).getTime();
                        if (file.lastModified() == lastMod) {
                            // already cached and up to date,
                            // nothing more to do
                            return;
                        } else {
                            fileInfoRS.updateTimestamp(2,
                                    new Timestamp(file.lastModified()));
                            fileInfoRS.updateRow();
                        }
                    }
                } finally {
                    fileInfoRS.close();
                    fileInfoPS.close();
                }

                if (fileId == null) {
                    PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO FILES(PATH, REPOSITORY, " +
                            "LAST_MODIFICATION) VALUES (?,?,?)",
                            Statement.RETURN_GENERATED_KEYS);
                    insert.setString(1, filePath);
                    insert.setInt(2, reposId);
                    insert.setTimestamp(3, new Timestamp(file.lastModified()));
                    insert.executeUpdate();
                    fileId = getGeneratedIntKey(insert);
                    insert.close();
                }

                assert fileId != null;

                Map<String, Integer> authors =
                        getAuthors(conn, history, reposId);

                PreparedStatement removeChanges = conn.prepareStatement(
                        "DELETE FROM FILECHANGES WHERE FILE = ?");
                removeChanges.setInt(1, fileId);
                removeChanges.executeUpdate();
                removeChanges.close();

                PreparedStatement findChangeset = conn.prepareStatement(
                        "SELECT ID FROM CHANGESETS " +
                        "WHERE REPOSITORY = ? AND REVISION = ?");

                PreparedStatement addChangeset = conn.prepareStatement(
                        "INSERT INTO CHANGESETS" +
                        "(REPOSITORY, REVISION, AUTHOR, TIME, MESSAGE) " +
                        "VALUES (?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);

                PreparedStatement addFileChange = conn.prepareStatement(
                      "INSERT INTO FILECHANGES(FILE, CHANGESET) VALUES (?,?)");

                for (HistoryEntry entry : history.getHistoryEntries()) {
                    storeEntry(
                            fileId, reposId, entry, authors,
                            findChangeset, addChangeset, addFileChange);
                }

                findChangeset.close();
                addChangeset.close();
                addFileChange.close();

                conn.commit();
            } finally {
                connectionManager.releaseConnection(conn);
            }
        } catch (SQLException sqle) {
            throw new HistoryException(sqle);
        }
    }

    /**
     * Get a map from author names to their ids in the database.
     * @param conn the connection to the database
     * @param history the history to get the author names from
     * @param reposId the id of the repository
     * @return a map from author names to author ids
     */
    private Map<String, Integer> getAuthors(
            Connection conn, History history, int reposId)
            throws SQLException {
        HashMap<String, Integer> map = new HashMap<String, Integer>();
        PreparedStatement check = conn.prepareStatement(
                "SELECT ID FROM AUTHORS WHERE REPOSITORY = ? AND NAME = ?");
        PreparedStatement insert = null;
        try {
            check.setInt(1, reposId);

            for (HistoryEntry entry : history.getHistoryEntries()) {
                String author = entry.getAuthor();
                Integer id = null;

                check.setString(2, author);
                ResultSet rs = check.executeQuery();
                try {
                    if (rs.next()) {
                        id = rs.getInt(1);
                    }
                } finally {
                    rs.close();
                }

                if (id == null) {
                    if (insert == null) {
                        insert = conn.prepareStatement(
                                "INSERT INTO AUTHORS (REPOSITORY, NAME) " +
                                "VALUES (?,?)",
                                Statement.RETURN_GENERATED_KEYS);
                    }
                    insert.setInt(1, reposId);
                    insert.setString(2, author);
                    insert.executeUpdate();
                    id = getGeneratedIntKey(insert);
                }

                assert id != null;
                map.put(author, id);
            }
        } finally {
            check.close();
            if (insert != null) {
                insert.close();
            }
        }

        return map;
    }

    /**
     * Store a {@code HistoryEntry} in the database.
     *
     * @param fileId the id of the file to store history for
     * @param reposId the id of the repository
     * @param entry the {@code HistoryEntry} to store
     * @param authors map from author names to author ids
     * @param findChangeset prepared statement to find the changeset
     * @param addChangeset prepared statement to add a changeset if it is not
     * already in the database
     * @param addFileChange prepared statement to associate a changeset with
     * a file
     */
    private void storeEntry(int fileId, int reposId, HistoryEntry entry,
            Map<String, Integer> authors, PreparedStatement findChangeset,
            PreparedStatement addChangeset, PreparedStatement addFileChange)
        throws SQLException
    {
        findChangeset.setInt(1, reposId);
        findChangeset.setString(2, entry.getRevision());
        ResultSet changeset = findChangeset.executeQuery();
        Integer changesetId = null;
        try {
            if (changeset.next()) {
                changesetId = changeset.getInt(1);
            }
        } finally {
            changeset.close();
        }

        if (changesetId == null) {
            addChangeset.setInt(1, reposId);
            addChangeset.setString(2, entry.getRevision());
            addChangeset.setInt(3, authors.get(entry.getAuthor()));
            addChangeset.setTimestamp(4, new Timestamp(entry.getDate().getTime()));
            addChangeset.setString(5, entry.getMessage());
            addChangeset.executeUpdate();
            changesetId = getGeneratedIntKey(addChangeset);
        }

        assert changesetId != null;

        addFileChange.setInt(1, fileId);
        addFileChange.setInt(2, changesetId);
        addFileChange.executeUpdate();
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
