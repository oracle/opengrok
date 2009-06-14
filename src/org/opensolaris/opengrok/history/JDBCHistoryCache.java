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
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.jdbc.ConnectionManager;
import org.opensolaris.opengrok.jdbc.ConnectionResource;
import org.opensolaris.opengrok.jdbc.InsertQuery;
import org.opensolaris.opengrok.jdbc.PreparedQuery;

class JDBCHistoryCache implements HistoryCache {

    private static final String DERBY_EMBEDDED_DRIVER =
            "org.apache.derby.jdbc.EmbeddedDriver";

    private static final String SCHEMA = "APP";

    /** The names of all the tables created by this class. */
    private static final String[] TABLES = {
        "REPOSITORIES", "FILES", "AUTHORS", "CHANGESETS", "FILECHANGES"
    };

    private ConnectionManager connectionManager;

    private final String jdbcDriverClass;
    private final String jdbcConnectionURL;

    /**
     * Create a new cache instance with the default JDBC driver and URL.
     */
    JDBCHistoryCache() {
        this(DERBY_EMBEDDED_DRIVER,
                "jdbc:derby:" +
                RuntimeEnvironment.getInstance().getDataRootPath() +
                File.separator + "cachedb;create=true");
    }

    /**
     * Create a new cache instance with the specified JDBC driver and URL.
     *
     * @param jdbcDriverClass JDBC driver class to access the database backend
     * @param url the JDBC url to the database
     */
    JDBCHistoryCache(String jdbcDriverClass, String url) {
        this.jdbcDriverClass = jdbcDriverClass;
        this.jdbcConnectionURL = url;
    }

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
            // Create a descending index on the identity column to allow
            // faster retrieval of history in reverse chronological order in
            // get() and maximum value in getLatestCachedRevision().
            s.executeUpdate("CREATE UNIQUE INDEX IDX_CHANGESETS_ID_DESC " +
                    "ON CHANGESETS(ID DESC)");
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
            connectionManager =
                    new ConnectionManager(jdbcDriverClass, jdbcConnectionURL);
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

    private static final PreparedQuery IS_DIR_IN_CACHE = new PreparedQuery(
            "SELECT F.ID FROM FILES F, REPOSITORIES R " +
            "WHERE F.REPOSITORY = R.ID AND R.PATH = ? AND " +
            "F.PATH LIKE ? || '/%'");

    // We do check the return value from ResultSet.next(), but PMD doesn't
    // understand it, so suppress the warning.
    @SuppressWarnings("PMD.CheckResultSet")
    public boolean hasCacheForDirectory(File file, Repository repository)
            throws HistoryException {
        assert file.isDirectory();
        try {
            final ConnectionResource conn =
                    connectionManager.getConnectionResource();
            try {
                PreparedStatement ps = conn.getStatement(IS_DIR_IN_CACHE);
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
        return getRelativePath(filePath, reposPath);
    }

    /**
     * Get the path of a file relative to the source root.
     * @param file the file to get the path for
     * @return relative path for {@code file} with unix file separators
     */
    private static String getSourceRootRelativePath(File file)
            throws HistoryException {
        String filePath = toUnixPath(file);
        String rootPath = RuntimeEnvironment.getInstance().getSourceRootPath();
        return getRelativePath(filePath, rootPath);
    }

    /**
     * Get the path of a file relative to the specified root directory.
     * @param filePath the canonical path of the file to get the relative
     * path for
     * @param rootPath the canonical path of the root directory
     * @return relative path with unix file separators
     */
    private static String getRelativePath(String filePath, String rootPath) {
        assert filePath.startsWith(rootPath);
        return filePath.substring(rootPath.length());
    }

    /**
     * Statement that gets the history for the specified file and repository.
     * The result is ordered in reverse chronological order to match the
     * required ordering for {@link HistoryCache#get(File, Repository)}.
     */
    private static final PreparedQuery GET_HISTORY = new PreparedQuery(
            "SELECT CS.REVISION, A.NAME, CS.TIME, CS.MESSAGE, F2.PATH " +
            "FROM CHANGESETS CS, FILECHANGES FC, REPOSITORIES R, " +
            "FILES F, AUTHORS A, FILECHANGES FC2, FILES F2 " +
            "WHERE R.PATH = ? AND F.PATH LIKE ? ESCAPE '#' AND " +
            "F.REPOSITORY = R.ID AND A.REPOSITORY = R.ID AND " +
            "CS.ID = FC.CHANGESET AND R.ID = CS.REPOSITORY AND " +
            "FC.FILE = F.ID AND A.ID = CS.AUTHOR AND " +
            "CS.ID = FC2.CHANGESET AND FC2.FILE = F2.ID " +
            "ORDER BY CS.ID DESC");

    public History get(File file, Repository repository)
            throws HistoryException {
        final String filePath = getSourceRootRelativePath(file);
        final String reposPath = toUnixPath(repository.getDirectoryName());
        final ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        try {
            final ConnectionResource conn =
                    connectionManager.getConnectionResource();
            try {
                PreparedStatement ps = conn.getStatement(GET_HISTORY);
                ps.setString(1, reposPath);
                ps.setString(2, createPathPattern(
                        filePath, file.isDirectory(), '#'));
                ResultSet rs = ps.executeQuery();
                try {
                    String currentRev = null;
                    HistoryEntry entry = null;
                    while (rs.next()) {
                        String revision = rs.getString(1);
                        if (!revision.equals(currentRev)) {
                            currentRev = revision;
                            String author = rs.getString(2);
                            Timestamp time = rs.getTimestamp(3);
                            String message = rs.getString(4);
                            entry = new HistoryEntry(
                                    revision, time, author, message, true);
                            entries.add(entry);
                        }
                        String fileName = rs.getString(5);
                        entry.addFile(fileName);
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

    /**
     * Create a pattern that can be used to match against a file name or a
     * directory name with LIKE.
     *
     * @param path the path name to create the pattern from
     * @param isDir {@code true} if path is a directory and should match all
     * files living in that directory (or in one of the subdirectories)
     * @param escape the escape character used in the LIKE query
     * @return a pattern for use in LIKE queries
     */
    private static String createPathPattern(
            String path, boolean isDir, char escape) {
        StringBuilder escaped = new StringBuilder(path.length() + 2);
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '%' || c == '_' || c == escape) {
                escaped.append(escape);
            }
            escaped.append(c);
        }
        if (isDir) {
            escaped.append("/%");
        }
        return escaped.toString();
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
                // The tables may have grown significantly and made the
                // index cardinality statistics outdated. Call this method
                // as a workaround to keep the statistics up to date.
                // Without this, we have observed very bad performance when
                // calling get(), because the outdated statistics can make
                // the optimizer choose a sub-optimal join strategy.
                updateIndexCardinalityStatistics(conn);
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

        // getHistoryEntries() returns the entries in reverse chronological
        // order, but we want to insert them in chronological order so that
        // their auto-generated identity column can be used as a chronological
        // ordering column. Otherwise, incremental updates will make the
        // identity column unusable for chronological ordering. So therefore
        // we walk the list backwards.
        List<HistoryEntry> entries = history.getHistoryEntries();
        for (ListIterator<HistoryEntry> it =
                entries.listIterator(entries.size());
                it.hasPrevious();) {
            HistoryEntry entry = it.previous();
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
     * <p>
     * Make sure Derby's index cardinality statistics are up to date.
     * Otherwise, the optimizer may choose a bad execution strategy for
     * some queries. This method should be called if the size of the tables
     * has changed significantly.
     * </p>
     *
     * <p>
     * This is a workaround for the problems described in
     * <a href="https://issues.apache.org/jira/browse/DERBY-269">DERBY-269</a> and
     * <a href="https://issues.apache.org/jira/browse/DERBY-3788">DERBY-3788</a>.
     * When automatic update of index cardinality statistics has been
     * implemented in Derby, the workaround may be removed.
     * </p>
     *
     * <p>
     * Note that this method uses a system procedure introduced in Derby 10.5.
     * If the Derby version used is less than 10.5, this method is a no-op.
     * </p>
     */
    private void updateIndexCardinalityStatistics(ConnectionResource conn)
            throws SQLException {
        DatabaseMetaData dmd = conn.getMetaData();
        int major = dmd.getDatabaseMajorVersion();
        int minor = dmd.getDatabaseMinorVersion();
        if (major > 10 || (major == 10 && minor >= 5)) {
            PreparedStatement ps = conn.prepareStatement(
                    "CALL SYSCS_UTIL.SYSCS_UPDATE_STATISTICS(?, ?, NULL)");
            try {
                ps.setString(1, SCHEMA);
                for (String table : TABLES) {
                    ps.setString(2, table);
                    ps.execute();
                }
                conn.commit();
            } finally {
                ps.close();
            }
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

    private static PreparedQuery GET_LATEST_REVISION = new PreparedQuery(
            "SELECT REVISION FROM CHANGESETS WHERE ID = " +
            "(SELECT MAX(CS.ID) FROM CHANGESETS CS, REPOSITORIES R " +
            "WHERE CS.REPOSITORY = R.ID AND R.PATH = ?)");

    public String getLatestCachedRevision(Repository repository)
            throws HistoryException {
        try {
            final ConnectionResource conn =
                    connectionManager.getConnectionResource();
            try {
                PreparedStatement ps = conn.getStatement(GET_LATEST_REVISION);
                ps.setString(1, toUnixPath(repository.getDirectoryName()));
                ResultSet rs = ps.executeQuery(); // NOPMD (we do check next)
                try {
                    return rs.next() ? rs.getString(1) : null;
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
}
