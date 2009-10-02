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
import java.io.InputStream;
import java.io.IOException;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.jdbc.ConnectionManager;
import org.opensolaris.opengrok.jdbc.ConnectionResource;
import org.opensolaris.opengrok.jdbc.InsertQuery;
import org.opensolaris.opengrok.jdbc.PreparedQuery;

class JDBCHistoryCache implements HistoryCache {

    private static final String SCHEMA = "APP";

    /** The names of all the tables created by this class. */
    private static final String[] TABLES = {
        "REPOSITORIES", "FILES", "AUTHORS", "CHANGESETS", "FILECHANGES",
        "DIRECTORIES", "DIRCHANGES"
    };

    /**
     * The number of times to retry an operation that failed in a way that
     * indicates that it may succeed if it's tried again.
     */
    private static final int MAX_RETRIES = 2;

    /**
     * The maximum number of characters in commit messages. Longer messages
     * will be truncated.
     */
    private static final int MAX_MESSAGE_LENGTH = 32672;

    private ConnectionManager connectionManager;

    private final String jdbcDriverClass;
    private final String jdbcConnectionURL;

    /** The id to be used for the next row inserted into FILES. */
    private final AtomicInteger nextFileId = new AtomicInteger();

    /** The id to be used for the next row inserted into DIRECTORIES. */
    private final AtomicInteger nextDirId = new AtomicInteger();

    /** The id to be used for the next row inserted into CHANGESETS. */
    private final AtomicInteger nextChangesetId = new AtomicInteger();

    /** SQL queries used by this class. */
    private final static Properties QUERIES = new Properties();
    static {
        Class klazz = JDBCHistoryCache.class;
        InputStream in = null;
        try {
            in = klazz.getResourceAsStream(klazz.getSimpleName() + "_queries.properties");
            if ( in != null ) {
            QUERIES.load(in); }
        } catch (IOException ioe) {
            throw new ExceptionInInitializerError(ioe);
        } finally {
          try {
             if (in != null ) { 
              in.close(); }
          } catch (IOException ioe) {
            //ignore
            //throw new ExceptionInInitializerError(ioe);
          } 
        }
    }

    /**
     * Create a new cache instance with the default JDBC driver and URL.
     */
    JDBCHistoryCache() {
        this(RuntimeEnvironment.getInstance().getDatabaseDriver(),
             RuntimeEnvironment.getInstance().getDatabaseUrl());
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

    /**
     * Handle an {@code SQLException}. If the exception indicates that the
     * operation may succeed if it's retried and the number of attempts hasn't
     * exceeded the limit defined by {@link #MAX_RETRIES}, ignore it and let
     * the caller retry the operation. Otherwise, re-throw the exception.
     *
     * @param sqle the exception to handle
     * @param attemptNo the attempt number, first attempt is 0
     * @throws SQLException if the operation shouldn't be retried
     */
    private static void handleSQLException(SQLException sqle, int attemptNo)
            throws SQLException {
        // TODO: When we only support JDK 6 or later, check for
        // SQLTransactionRollbackException instead of SQLState. Or
        // perhaps SQLTransientException.
        boolean isTransient = false;
        Throwable t = sqle;
        do {
            if (t instanceof SQLException) {
                String sqlState = ((SQLException) t).getSQLState();
                isTransient = (sqlState != null) && sqlState.startsWith("40");
            }
            t = t.getCause();
        } while (!isTransient && t != null);

        if (isTransient && attemptNo < MAX_RETRIES) {
            Logger logger = OpenGrokLogger.getLogger();
            logger.info("Transient database failure detected. Retrying.");
            logger.log(Level.FINE, "Transient database failure details:", sqle);
        } else {
            throw sqle;
        }
    }

    /**
     * Get the SQL text for a name query.
     * @param key name of the query
     * @return SQL text for the query
     */
    private static String getQuery(String key) {
        return QUERIES.getProperty(key);
    }

    private void initDB(Statement s) throws SQLException {
        // TODO Store a database version which is incremented on each
        // format change. When a version change is detected, drop the database
        // (or possibly in the future: add some upgrade code that makes the
        // necessary changes between versions). For now, just check if the
        // tables exist, and create them if necessary.

        DatabaseMetaData dmd = s.getConnection().getMetaData();

        if (!tableExists(dmd, SCHEMA, "REPOSITORIES")) {
            s.execute(getQuery("createTableRepositories"));
        }

        if (!tableExists(dmd, SCHEMA, "DIRECTORIES")) {
            s.execute(getQuery("createTableDirectories"));
        }

        if (!tableExists(dmd, SCHEMA, "FILES")) {
            s.execute(getQuery("createTableFiles"));
        }

        if (!tableExists(dmd, SCHEMA, "AUTHORS")) {
            s.execute(getQuery("createTableAuthors"));
        }

        if (!tableExists(dmd, SCHEMA, "CHANGESETS")) {
            s.execute(getQuery("createTableChangesets"));
            // Create a composite index on the repository in ascending order
            // and the id in descending order. This index may allow faster
            // retrieval of history in reverse chronological order.
            s.execute(getQuery("createIndexChangesetsRepoIdDesc"));
        }

        if (!tableExists(dmd, SCHEMA, "DIRCHANGES")) {
            s.execute(getQuery("createTableDirchanges"));
        }

        if (!tableExists(dmd, SCHEMA, "FILECHANGES")) {
            s.execute(getQuery("createTableFilechanges"));
        }

        initIdGenerator(s, "getMaxFileId", nextFileId);
        initIdGenerator(s, "getMaxDirId", nextDirId);
        initIdGenerator(s, "getMaxChangesetId", nextChangesetId);
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

    /**
     * Initialize the {@code AtomicInteger} object that holds the value of
     * the id to use for the next row in a certain table. If there are rows
     * in the table, take the maximum value and increment it by one. Otherwise,
     * the {@code AtomicInteger} will be left at its current value (presumably
     * 0).
     *
     * @param s a statement object on which the max query is executed
     * @param stmtKey name of the query to execute in order to get max id
     * @param generator the {@code AtomicInteger} object to initialize
     */
    private static void initIdGenerator(
            Statement s, String stmtKey, AtomicInteger generator)
            throws SQLException {
        ResultSet rs = s.executeQuery(getQuery(stmtKey));
        try {
            if (rs.next()) {
                int val = rs.getInt(1);
                if (!rs.wasNull()) {
                    generator.set(val + 1);
                }
            }
        } finally {
            rs.close();
        }
    }

    @Override
    public void initialize() throws HistoryException {
        try {
            connectionManager =
                    new ConnectionManager(jdbcDriverClass, jdbcConnectionURL);
            for (int i = 0;; i++) {
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
                    // Success! Break out of the loop.
                    return;
                } catch (SQLException sqle) {
                    handleSQLException(sqle, i);
                } finally {
                    connectionManager.releaseConnection(conn);
                }
            }
        } catch (Exception e) {
            throw new HistoryException(e);
        }
    }

    private static final PreparedQuery IS_DIR_IN_CACHE =
            new PreparedQuery(getQuery("hasCacheForDirectory"));

    // We do check the return value from ResultSet.next(), but PMD doesn't
    // understand it, so suppress the warning.
    @SuppressWarnings("PMD.CheckResultSet")
    @Override
    public boolean hasCacheForDirectory(File file, Repository repository)
            throws HistoryException {
        assert file.isDirectory();
        try {
            for (int i = 0;; i++) {
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
                } catch (SQLException sqle) {
                    handleSQLException(sqle, i);
                } finally {
                    connectionManager.releaseConnection(conn);
                }
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
     * Get the base name of a path (with unix file separators).
     *
     * @param fullPath the full path of the file with unix file separators
     * @return the base name of the file
     */
    private static String getBaseName(String fullPath) {
        int idx = fullPath.lastIndexOf('/');
        return (idx >= 0) ? fullPath.substring(idx + 1) : fullPath;
    }

    /**
     * Get the path to the parent of the specified file.
     *
     * @param fullPath the full path of the file with unix file separators
     * @return the full path of the file's parent
     */
    private static String getParentPath(String fullPath) {
        int idx = fullPath.lastIndexOf('/');
        return (idx >= 0) ? fullPath.substring(0, idx) : fullPath;
    }

    /**
     * Split a full (unix-style) path into an array of path elements.
     *
     * @param fullPath the full unix-style path name
     * @return an array with each separate element of the path
     * @throws IllegalArgumentException if fullPath doesn't start with '/'
     */
    private static String[] splitPath(String fullPath) {
        if (fullPath.isEmpty() || fullPath.charAt(0) != '/') {
            throw new IllegalArgumentException("Not a full path: " + fullPath);
        }
        return fullPath.substring(1).split("/");
    }

    /**
     * Reconstruct a path previously split by {@link #splitPath(String)}, or
     * possibly just a part of it (only the {@code num} first elements will
     * be used).
     *
     * @param pathElts the elements of the path
     * @param num the number of elements to use when reconstructing the path
     * @return a path name
     */
    private static String unsplitPath(String[] pathElts, int num) {
        StringBuilder out = new StringBuilder("");
        for (int i = 0; i < num; i++) {
            out.append("/").append(pathElts[i]);
        }
        return out.toString();
    }

    /**
     * Truncate a string to the given length.
     *
     * @param str the string to truncate
     * @param length the length of the string after truncation
     * @return the truncated string
     * @throws IllegalArgumentException if the string is not longer than the
     * specified length
     */
    private static String truncate(String str, int length) {
        if (str.length() < length) {
            throw new IllegalArgumentException();
        }
        String suffix = " (...)";
        return length < suffix.length() ?
            str.substring(0, length) :
            (str.substring(0, length - suffix.length()) + suffix);
    }

    /**
     * Statement that gets the history for the specified file and repository.
     * The result is ordered in reverse chronological order to match the
     * required ordering for {@link HistoryCache#get(File, Repository)}.
     */
    private static final PreparedQuery GET_FILE_HISTORY =
            new PreparedQuery(getQuery("getFileHistory"));

    /**
     * Statement that gets the history for all files matching a pattern in the
     * given repository. The result is ordered in reverse chronological order
     * to match the required ordering for
     * {@link HistoryCache#get(File, Repository)}.
     */
    private static final PreparedQuery GET_DIR_HISTORY =
            new PreparedQuery(getQuery("getDirHistory"));

    /** Statement that retrieves all the files touched by a given changeset. */
    private static final PreparedQuery GET_CS_FILES =
            new PreparedQuery(getQuery("getFilesInChangeset"));

    @Override
    public History get(File file, Repository repository, boolean withFiles)
            throws HistoryException {
        try {
            for (int i = 0;; i++) {
                try {
                    return getHistory(file, repository, withFiles);
                } catch (SQLException sqle) {
                    handleSQLException(sqle, i);
                }
            }
        } catch (SQLException sqle) {
            throw new HistoryException(sqle);
        }
    }

    /**
     * Helper for {@link #get(File, Repository)}.
     */
    private History getHistory(
            File file, Repository repository, boolean withFiles)
            throws HistoryException, SQLException {
        final String filePath = getSourceRootRelativePath(file);
        final String reposPath = toUnixPath(repository.getDirectoryName());
        final ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        final ConnectionResource conn =
                connectionManager.getConnectionResource();
        try {
            final PreparedStatement ps;
            if (file.isDirectory()) {
                // Fetch history for all files under this directory.
                ps = conn.getStatement(GET_DIR_HISTORY);
                ps.setString(2, filePath);
            } else {
                // Fetch history for a single file only.
                ps = conn.getStatement(GET_FILE_HISTORY);
                ps.setString(2, getParentPath(filePath));
                ps.setString(3, getBaseName(filePath));
            }
            ps.setString(1, reposPath);

            final PreparedStatement filePS =
                    withFiles ? conn.getStatement(GET_CS_FILES) : null;

            ResultSet rs = ps.executeQuery();
            try {
                while (rs.next()) {
                    // Get the information about a changeset
                    String revision = rs.getString(1);
                    String author = rs.getString(2);
                    Timestamp time = rs.getTimestamp(3);
                    String message = rs.getString(4);
                    HistoryEntry entry = new HistoryEntry(
                                revision, time, author, message, true);
                    entries.add(entry);

                    // Fill the list of files touched by the changeset, if
                    // requested.
                    if (withFiles) {
                        int changeset = rs.getInt(5);
                        filePS.setInt(1, changeset);

                        // We do check next(), but PMD doesn't understand it.
                        ResultSet fileRS = filePS.executeQuery(); // NOPMD
                        try {
                            while (fileRS.next()) {
                                entry.addFile(fileRS.getString(1));
                            }
                        } finally {
                            fileRS.close();
                        }
                    }
                }
            } finally {
                rs.close();
            }
        } finally {
            connectionManager.releaseConnection(conn);
        }

        History history = new History();
        history.setHistoryEntries(entries);
        return history;
    }

    private static PreparedQuery GET_REPOSITORY =
            new PreparedQuery(getQuery("getRepository"));

    private static InsertQuery INSERT_REPOSITORY =
            new InsertQuery(getQuery("addRepository"));

    @Override
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

    private static InsertQuery ADD_CHANGESET =
            new InsertQuery(getQuery("addChangeset"));

    private static PreparedQuery ADD_DIRCHANGE =
            new PreparedQuery(getQuery("addDirchange"));

    private static PreparedQuery ADD_FILECHANGE =
            new PreparedQuery(getQuery("addFilechange"));

    private void storeHistory(ConnectionResource conn, History history,
            Repository repository) throws SQLException {

        Integer reposId = null;
        Map<String, Integer> authors = null;
        Map<String, Integer> files = null;
        Map<String, Integer> directories = null;
        PreparedStatement addChangeset = null;
        PreparedStatement addDirchange = null;
        PreparedStatement addFilechange = null;

        for (int i = 0;; i++) {
            try {
                if (reposId == null) {
                    reposId = getRepositoryId(conn, repository);
                    conn.commit();
                }

                if (authors == null) {
                    authors = getAuthors(conn, history, reposId);
                    conn.commit();
                }

                if (directories == null || files == null) {
                    Map<String, Integer> dirs = new HashMap<String, Integer>();
                    Map<String, Integer> fls = new HashMap<String, Integer>();
                    getFilesAndDirectories(conn, history, reposId, dirs, fls);
                    conn.commit();
                    directories = dirs;
                    files = fls;
                }

                if (addChangeset == null) {
                    addChangeset = conn.getStatement(ADD_CHANGESET);
                }

                if (addDirchange == null) {
                    addDirchange = conn.getStatement(ADD_DIRCHANGE);
                }

                if (addFilechange == null) {
                    addFilechange = conn.getStatement(ADD_FILECHANGE);
                }

                // Success! Break out of the loop.
                break;

            } catch (SQLException sqle) {
                handleSQLException(sqle, i);
                conn.rollback();
            }
        }

        addChangeset.setInt(1, reposId);

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
            retry:
            for (int i = 0;; i++) {
                try {
                    addChangeset.setString(2, entry.getRevision());
                    addChangeset.setInt(3, authors.get(entry.getAuthor()));
                    addChangeset.setTimestamp(4,
                            new Timestamp(entry.getDate().getTime()));
                    String msg = entry.getMessage();
                    // Truncate the message if it can't fit in a VARCHAR
                    // (bug #11663).
                    if (msg.length() > MAX_MESSAGE_LENGTH) {
                        msg = truncate(msg, MAX_MESSAGE_LENGTH);
                    }
                    addChangeset.setString(5, msg);
                    int changesetId = nextChangesetId.getAndIncrement();
                    addChangeset.setInt(6, changesetId);
                    addChangeset.executeUpdate();

                    // Add one row for each file in FILECHANGES, and one row
                    // for each path element of the directories in DIRCHANGES.
                    Set<String> addedDirs = new HashSet<String>();
                    addDirchange.setInt(1, changesetId);
                    addFilechange.setInt(1, changesetId);
                    for (String file : entry.getFiles()) {
                        String fullPath = toUnixPath(file);
                        int fileId = files.get(fullPath);
                        addFilechange.setInt(2, fileId);
                        addFilechange.executeUpdate();
                        String[] pathElts = splitPath(fullPath);
                        for (int j = 0; j < pathElts.length; j++) {
                            String dir = unsplitPath(pathElts, j);
                            // Only add to DIRCHANGES if we haven't already
                            // added this dir/changeset combination.
                            if (!addedDirs.contains(dir)) {
                                addDirchange.setInt(2, directories.get(dir));
                                addDirchange.executeUpdate();
                                addedDirs.add(dir);
                            }
                        }
                    }

                    conn.commit();

                    // Successfully added the entry. Break out of retry loop.
                    break retry;

                } catch (SQLException sqle) {
                    handleSQLException(sqle, i);
                    conn.rollback();
                }
            }
        }
    }

    /**
     * Optimize how the cache is stored on disk. In particular, make sure
     * index cardinality statistics are up to date, and perform a checkpoint
     * to make sure all changes are forced to the tables on disk and that
     * the unneeded transaction log is deleted.
     *
     * @throws HistoryException if an error happens when optimizing the cache
     */
    @Override
    public void optimize() throws HistoryException {
        try {
            final ConnectionResource conn =
                    connectionManager.getConnectionResource();
            try {
                updateIndexCardinalityStatistics(conn);
                Statement s = conn.createStatement();
                try {
                    s.execute("CALL SYSCS_UTIL.SYSCS_CHECKPOINT_DATABASE()");
                } finally {
                    s.close();
                }
            } finally {
                connectionManager.releaseConnection(conn);
            }
        } catch (SQLException sqle) {
            throw new HistoryException(sqle);
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
     * Without this workaround, poor performance has been observed in
     * {@code get()} due to bad choices made by the optimizer.
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
                    retry:
                    for (int i = 0;; i++) {
                        try {
                            ps.execute();
                            // Successfully executed statement. Break out of
                            // retry loop.
                            break retry;
                        } catch (SQLException sqle) {
                            handleSQLException(sqle, i);
                            conn.rollback();
                        }
                    }
                    conn.commit();
                }
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

    private final static PreparedQuery GET_AUTHORS =
            new PreparedQuery(getQuery("getAuthors"));

    private final static InsertQuery ADD_AUTHOR =
            new InsertQuery(getQuery("addAuthor"));

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
                conn.commit();
            }
        }

        return map;
    }

    private static PreparedQuery GET_DIRS =
            new PreparedQuery(getQuery("getDirectories"));

    private static PreparedQuery GET_FILES =
            new PreparedQuery(getQuery("getFiles"));

    private static InsertQuery INSERT_DIR =
            new InsertQuery(getQuery("addDirectory"));

    private static InsertQuery INSERT_FILE =
            new InsertQuery(getQuery("addFile"));

    /**
     * Build maps from directory names and file names to their respective
     * identifiers in the database. The directories and files that are not
     * already in the database, are added to it.
     *
     * @param conn the connection to the database
     * @param history the history to get the file and directory names from
     * @param reposId the id of the repository
     * @param dirMap a map which will be filled with directory names and ids
     * @param fileMap a map which will be filled with file names and ids
     */
    private void getFilesAndDirectories(
            ConnectionResource conn, History history, int reposId,
            Map<String, Integer> dirMap, Map<String, Integer> fileMap)
            throws SQLException {

        populateFileOrDirMap(conn.getStatement(GET_DIRS), reposId, dirMap);
        populateFileOrDirMap(conn.getStatement(GET_FILES), reposId, fileMap);

        int insertCount = 0;

        PreparedStatement insDir = conn.getStatement(INSERT_DIR);
        PreparedStatement insFile = conn.getStatement(INSERT_FILE);
        for (HistoryEntry entry : history.getHistoryEntries()) {
            for (String file : entry.getFiles()) {
                String fullPath = toUnixPath(file);
                // Add the file to the database and to the map if it isn't
                // there already. Assumption: If the file is in the database,
                // all its parent directories are also there.
                if (!fileMap.containsKey(fullPath)) {
                    // Get the dir id for this file, potentially adding the
                    // parent directories to the db and to dirMap.
                    int dir = addAllDirs(insDir, reposId, fullPath, dirMap);
                    int fileId = nextFileId.getAndIncrement();
                    insFile.setInt(1, dir);
                    insFile.setString(2, getBaseName(fullPath));
                    insFile.setInt(3, fileId);
                    insFile.executeUpdate();
                    fileMap.put(fullPath, fileId);

                    // Commit every now and then to allow the database to free
                    // resources (like locks and transaction log), but not too
                    // frequently, since that may kill the performance. It is
                    // OK not to commit for every file added, since the worst
                    // thing that could happen is that we need to re-insert
                    // the files added since the last commit in case of a crash.
                    insertCount++;
                    if (insertCount % 30 == 0) {
                        conn.commit();
                    }
                }
            }
        }
    }

    /**
     * Populate a map with all path/id combinations found in the FILES or
     * DIRECTORIES tables associated with a specified repository id.
     *
     * @param ps the statement used to get path names and ids from the correct
     * table. It should take one parameter: the repository id.
     * @param reposId the id of the repository to scan
     * @param map the map into which to insert the path/id combinations
     */
    private void populateFileOrDirMap(
            PreparedStatement ps, int reposId, Map<String, Integer> map)
            throws SQLException {
        ps.setInt(1, reposId);
        ResultSet rs = ps.executeQuery();
        try {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getInt(2));
            }
        } finally {
            rs.close();
        }
    }

    /**
     * Add all the parent directories of a specified file to the database, if
     * they haven't already been added, and also put their paths and ids into
     * a map.
     *
     * @param ps statement that inserts a directory into the DIRECTORY table.
     * Takes three parameters: (1) the id of the repository, (2) the path of
     * the directory, and (3) the id to use for the directory.
     * @param reposId id of the repository to which the file belongs
     * @param fullPath the file whose parents to add
     * @param map a map from directory path to id for the directories already
     * in the database. When a new directory is added, it's also added to this
     * map.
     * @return the id of the first parent of {@code fullPath}
     */
    private int addAllDirs(
            PreparedStatement ps, int reposId, String fullPath,
            Map<String, Integer> map) throws SQLException {
        String[] pathElts = splitPath(fullPath);
        String parent = unsplitPath(pathElts, pathElts.length - 1);
        Integer dir = map.get(parent);
        if (dir == null) {
            for (int i = pathElts.length - 1; i >= 0; i--) {
                String path = unsplitPath(pathElts, i);
                if (map.containsKey(path)) {
                    // Assumption: If a directory already exists in the
                    // database, all its parents also exist.
                    break;
                }
                int dirId = nextDirId.getAndIncrement();
                ps.setInt(1, reposId);
                ps.setString(2, path);
                ps.setInt(3, dirId);
                ps.executeUpdate();
                map.put(path, dirId);
            }
            dir = map.get(parent);
        }
        return dir;
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

    private static PreparedQuery GET_LATEST_REVISION =
            new PreparedQuery(getQuery("getLatestCachedRevision"));

    @Override
    public String getLatestCachedRevision(Repository repository)
            throws HistoryException {
        try {
            for (int i = 0;; i++) {
                try {
                    return getLatestRevisionForRepository(repository);
                } catch (SQLException sqle) {
                    handleSQLException(sqle, i);
                }
            }
        } catch (SQLException sqle) {
            throw new HistoryException(sqle);
        }
    }

    /**
     * Helper for {@link #getLatestCachedRevision(Repository)}.
     */
    private String getLatestRevisionForRepository(Repository repository)
            throws SQLException {
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
    }
}
