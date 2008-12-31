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

    private ConnectionManager connectionManager;

    private static void initDB(Connection c) throws SQLException {
        // TODO Store a database version which is incremented on each
        // format change. When a version change is detected, drop the database
        // (or possibly in the future: add some upgrade code that makes the
        // necessary changes between versions). For now, just check if the
        // tables exist, and create them if necessary.

        c.setAutoCommit(false);
        Statement s = c.createStatement();
        DatabaseMetaData dmd = c.getMetaData();

        ResultSet schemaRS = s.executeQuery("VALUES CURRENT SCHEMA");
        schemaRS.next();
        String schema = schemaRS.getString(1);
        schemaRS.close();

        if (!tableExists(dmd, schema, "REPOSITORIES")) {
            s.executeUpdate("CREATE TABLE REPOSITORIES (" +
                    "ID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, " +
                    "PATH VARCHAR(32672) UNIQUE NOT NULL)");
        }

        if (!tableExists(dmd, schema, "FILES")) {
            s.executeUpdate("CREATE TABLE FILES (" +
                    "ID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, " +
                    "PATH VARCHAR(32672) NOT NULL, " +
                    "REPOSITORY INT NOT NULL REFERENCES REPOSITORIES " +
                    "ON DELETE CASCADE, " +
                    "LAST_MODIFICATION TIMESTAMP NOT NULL, " +
                    "UNIQUE (REPOSITORY, PATH))");
        }

        if (!tableExists(dmd, schema, "AUTHORS")) {
            s.executeUpdate("CREATE TABLE AUTHORS (" +
                    "ID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, " +
                    "REPOSITORY INT NOT NULL REFERENCES REPOSITORIES " +
                    "ON DELETE CASCADE, " +
                    "NAME VARCHAR(32672) NOT NULL, " +
                    "UNIQUE (REPOSITORY, NAME))");
        }

        if (!tableExists(dmd, schema, "CHANGESETS")) {
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

        if (!tableExists(dmd, schema, "FILECHANGES")) {
            s.executeUpdate("CREATE TABLE FILECHANGES (" +
                    "ID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, " +
                    "FILE INT NOT NULL REFERENCES FILES " +
                    "ON DELETE CASCADE, " +
                    "CHANGESET INT NOT NULL REFERENCES CHANGESETS " +
                    "ON DELETE CASCADE, " +
                    "UNIQUE (FILE, CHANGESET))");
        }

        s.close();
        c.commit();
    }

    private static boolean tableExists(
            DatabaseMetaData dmd, String schema, String table)
            throws SQLException {
        ResultSet rs = dmd.getTables(
                null, schema, table, new String[] {"TABLE"});
        boolean exists = rs.next();
        rs.close();
        return exists;
    }

    public void initialize() throws HistoryException {
        try {
            connectionManager = new ConnectionManager();
            final Connection conn = connectionManager.getConnection();
            try {
                initDB(conn);
            } finally {
                connectionManager.releaseConnection(conn);
            }
        } catch (Exception e) {
            throw new HistoryException(e);
        }
    }

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
                ps.setString(1, toUnixPath(repository.getDirectoryName()));
                ps.setString(2, getRelativePath(file, repository));
                ResultSet rs = ps.executeQuery();
                boolean found = rs.next();
                rs.close();
                ps.close();
                return found;
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
                ps.setString(1, reposPath);
                ps.setString(2, filePath);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String revision = rs.getString(1);
                    String author = rs.getString(2);
                    Timestamp time = rs.getTimestamp(3);
                    String message = rs.getString(4);
                    HistoryEntry entry = new HistoryEntry(
                            revision, time, author, message, true);
                    entries.add(entry);
                }
                rs.close();
                ps.close();
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

                PreparedStatement reposIdPS = conn.prepareStatement(
                        "SELECT ID FROM REPOSITORIES WHERE PATH = ?");
                reposIdPS.setString(1, reposPath);
                ResultSet reposIdRS = reposIdPS.executeQuery();

                Integer reposId = null;
                if (reposIdRS.next()) {
                    reposId = reposIdRS.getInt(1);
                }
                reposIdRS.close();
                reposIdPS.close();

                if (reposId == null) {
                    PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO REPOSITORIES(PATH) VALUES ?",
                            Statement.RETURN_GENERATED_KEYS);
                    insert.setString(1, reposPath);
                    insert.executeUpdate();
                    ResultSet keys = insert.getGeneratedKeys();
                    keys.next();
                    reposId = keys.getInt(1);
                    keys.close();
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

                final int fileId;
                final long lastMod;
                final boolean isCached = fileInfoRS.next();
                if (isCached) {
                    fileId = fileInfoRS.getInt(1);
                    lastMod = fileInfoRS.getTimestamp(2).getTime();
                    fileInfoRS.updateTimestamp(2,
                            new Timestamp(file.lastModified()));
                    fileInfoRS.updateRow();
                    // should only get one result
                    assert !fileInfoRS.next();
                } else {
                    lastMod = file.lastModified();
                    PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO FILES(PATH, REPOSITORY, " +
                            "LAST_MODIFICATION) VALUES (?,?,?)",
                            Statement.RETURN_GENERATED_KEYS);
                    insert.setString(1, filePath);
                    insert.setInt(2, reposId);
                    insert.setTimestamp(3, new Timestamp(lastMod));
                    insert.executeUpdate();
                    ResultSet autoGenRS = insert.getGeneratedKeys();
                    autoGenRS.next();
                    fileId = autoGenRS.getInt(1);
                    autoGenRS.close();
                    insert.close();
                }
                fileInfoRS.close();
                fileInfoPS.close();

                if (isCached && lastMod == file.lastModified()) {
                    // We have already cached the file with this timestamp,
                    // so there's nothing to do.
                    return;
                }

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
        check.setInt(1, reposId);
        PreparedStatement insert = null;

        for (HistoryEntry entry : history.getHistoryEntries()) {
            String author = entry.getAuthor();
            Integer id = null;

            check.setString(2, author);
            ResultSet rs = check.executeQuery();
            if (rs.next()) {
                id = rs.getInt(1);
            }
            rs.close();

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
                ResultSet keys = insert.getGeneratedKeys();
                keys.next();
                id = keys.getInt(1);
                keys.close();
            }

            assert id != null;
            map.put(author, id);
        }

        check.close();
        if (insert != null) {
            insert.close();
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
        if (changeset.next()) {
            changesetId = changeset.getInt(1);
        }
        changeset.close();

        if (changesetId == null) {
            addChangeset.setInt(1, reposId);
            addChangeset.setString(2, entry.getRevision());
            addChangeset.setInt(3, authors.get(entry.getAuthor()));
            addChangeset.setTimestamp(4, new Timestamp(entry.getDate().getTime()));
            addChangeset.setString(5, entry.getMessage());
            addChangeset.executeUpdate();
            ResultSet keys = addChangeset.getGeneratedKeys();
            keys.next();
            changesetId = keys.getInt(1);
            keys.close();
        }

        assert changesetId != null;

        addFileChange.setInt(1, fileId);
        addFileChange.setInt(2, changesetId);
        addFileChange.executeUpdate();
    }
}
