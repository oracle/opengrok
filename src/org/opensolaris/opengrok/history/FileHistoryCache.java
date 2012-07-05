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
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.history;

import java.beans.Encoder;
import java.beans.Expression;
import java.beans.PersistenceDelegate;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.IOUtils;

class FileHistoryCache implements HistoryCache {
    private final Object lock = new Object();

    static class FilePersistenceDelegate extends PersistenceDelegate {
        @Override
        protected Expression instantiate(Object oldInstance, Encoder out) {
            File f = (File)oldInstance;
            return new Expression(oldInstance, f.getClass(), "new", new Object[] {f.toString()});
        }
    }

    @Override
    public void initialize() {
        // nothing to do
    }

    @Override
    public void optimize() {
        // nothing to do
    }

    @Override
    public boolean supportsRepository(Repository repository) {
        // all repositories are supported
        return true;
    }

    /**
     * Get a <code>File</code> object describing the cache file.
     *
     * @param file the file to find the cache for
     * @return file that might contain cached history for <code>file</code>
     */
    private static File getCachedFile(File file) throws HistoryException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        StringBuilder sb = new StringBuilder();
        sb.append(env.getDataRootPath());
        sb.append(File.separatorChar);
        sb.append("historycache");

        try {
            String add = env.getPathRelativeToSourceRoot(file, 0);
            if (add.length() == 0) {
                add = File.separator;
            }
            sb.append(add);
            sb.append(".gz");
        } catch (IOException e) {
            throw new HistoryException("Failed to get path relative to source root for " + file, e);
        }

        return new File(sb.toString());
    }

    /**
     * Read history from a file.
     */
    private static History readCache(File file) throws IOException {
        final FileInputStream in = new FileInputStream(file);
        try {
            XMLDecoder d = new XMLDecoder(
                    new BufferedInputStream(new GZIPInputStream(in)));
            Object obj = d.readObject();
            d.close();
            return (History) obj;
        } finally {
            IOUtils.close(in);
        }
    }

    private void storeFile(History history, File file) throws HistoryException {

        File cache = getCachedFile(file);

        File dir = cache.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new HistoryException(
                    "Unable to create cache directory '" + dir + "'.");
        }

        // We have a problem that multiple threads may access the cache layer
        // at the same time. Since I would like to avoid read-locking, I just
        // serialize the write access to the cache file. The generation of the
        // cache file would most likely be executed during index generation, and
        // that happens sequencial anyway....
        // Generate the file with a temporary name and move it into place when
        // I'm done so I don't have to protect the readers for partially updated
        // files...
        final File output;
        try {
            output = File.createTempFile("oghist", null, dir);
            final FileOutputStream out = new FileOutputStream(output);
            try {
                XMLEncoder e = new XMLEncoder(
                        new BufferedOutputStream(new GZIPOutputStream(out)));
                e.setPersistenceDelegate(File.class, new FilePersistenceDelegate());
                e.writeObject(history);
                e.close();
            } finally {
                IOUtils.close(out);
            }
        } catch (IOException ioe) {
            throw new HistoryException("Failed to write history", ioe);
        }
        synchronized (lock) {
            if (!cache.delete() && cache.exists()) {
                if (!output.delete()) {
                    OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to remove temporary history cache file");
                }
                throw new HistoryException(
                        "Cachefile exists, and I could not delete it.");
            }
            if (!output.renameTo(cache)) {
                if (!output.delete()) {
                    OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to remove temporary history cache file");
                }
                throw new HistoryException("Failed to rename cache tmpfile.");
            }
        }
    }

    @Override
    public void store(History history, Repository repository)
            throws HistoryException {

        if (history.getHistoryEntries() == null) {
            return;
        }

        HashMap<String, List<HistoryEntry>> map =
                new HashMap<String, List<HistoryEntry>>();

        for (HistoryEntry e : history.getHistoryEntries()) {
            for (String s : e.getFiles()) {
                List<HistoryEntry> list = map.get(s);
                if (list == null) {
                    list = new ArrayList<HistoryEntry>();
                    map.put(s, list);
                }
                list.add(e);
            }
        }

        File root = RuntimeEnvironment.getInstance().getSourceRootFile();
        for (Map.Entry<String, List<HistoryEntry>> e : map.entrySet()) {
            for (HistoryEntry ent : e.getValue()) {
                ent.strip();
            }
            History hist = new History();
            hist.setHistoryEntries(e.getValue());
            File file = new File(root, e.getKey());
            if (!file.isDirectory()) {
                storeFile(hist, file);
            }
        }
    }

    @Override
    public History get(File file, Repository repository, boolean withFiles)
            throws HistoryException {
        File cache = getCachedFile(file);
        if (isUpToDate(file, cache)) {
            try {
                return readCache(cache);
            } catch (Exception e) {
                OpenGrokLogger.getLogger().log(Level.WARNING,
                        "Error when reading cache file '" + cache, e);
            }
        }

        final History history;
        long time;
        try {
            time = System.currentTimeMillis();
            history = repository.getHistory(file);
            time = System.currentTimeMillis() - time;
        } catch (UnsupportedOperationException e) {
            // In this case, we've found a file for which the SCM has no history
            // An example is a non-SCCS file somewhere in an SCCS-controlled
            // workspace.
            return null;
        }

        if (!file.isDirectory()) {
            // Don't cache history-information for directories, since the
            // history information on the directory may change if a file in
            // a sub-directory change. This will cause us to present a stale
            // history log until a the current directory is updated and
            // invalidates the cache entry.
            RuntimeEnvironment env = RuntimeEnvironment.getInstance();
            if ((cache != null) &&
                        (cache.exists() ||
                             (time > env.getHistoryReaderTimeLimit()))) {
                // retrieving the history takes too long, cache it!
                storeFile(history, file);
            }
        }
        return history;
    }

    /**
     * Check if the cache is up to date for the specified file.
     * @param file the file to check
     * @param cachedFile the file which contains the cached history for
     * the file
     * @return {@code true} if the cache is up to date, {@code false} otherwise
     */
    private boolean isUpToDate(File file, File cachedFile) {
        return cachedFile != null && cachedFile.exists() &&
                file.lastModified() <= cachedFile.lastModified();
    }

    /**
     * Check if the directory is in the cache.
     * @param directory the directory to check
     * @return {@code true} if the directory is in the cache
     */
    @Override
    public boolean hasCacheForDirectory(File directory, Repository repository)
            throws HistoryException {
        assert directory.isDirectory();
        Repository repos = HistoryGuru.getInstance().getRepository(directory);
        if (repos == null) {
            return true;
        }
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        File dir = env.getDataRootFile();
        dir = new File(dir, "historycache");
        try {
            dir = new File(dir, env.getPathRelativeToSourceRoot(new File(repos.getDirectoryName()), 0));
        } catch (IOException e) {
            throw new HistoryException("Could not resolve "+repos.getDirectoryName()+" relative to source root", e);
        }
        return dir.exists();
    }

    @Override
    public String getLatestCachedRevision(Repository repository) {
        return null;
    }

    @Override
    public Map<String, Date> getLastModifiedTimes(
            File directory, Repository repository) {
        // We don't have a good way to get this information from the file
        // cache, so leave it to the caller to find a reasonable time to
        // display (typically the last modified time on the file system).
        return Collections.emptyMap();
    }

    @Override
    public void clear(Repository repository) {
        // We only expect this method to be called if the cache supports
        // incremental update, so it's not implemented here for now.
        throw new UnsupportedOperationException();
    }

    @Override
    public String getInfo() {
        return getClass().getSimpleName();
    }
}
