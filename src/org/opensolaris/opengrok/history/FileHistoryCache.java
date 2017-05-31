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
 * Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.history;

import java.beans.Encoder;
import java.beans.Expression;
import java.beans.PersistenceDelegate;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.IOUtils;

/*
 * Class representing file based storage of per source file history.
 */
class FileHistoryCache implements HistoryCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileHistoryCache.class);

    private final Object lock = new Object();
    private String historyCacheDirName = "historycache";
    private String latestRevFileName = "OpenGroklatestRev";
    private boolean historyIndexDone = false;
    
    @Override
    public void setHistoryIndexDone() {
        historyIndexDone = true;
    }

    @Override
    public boolean isHistoryIndexDone() {
        return historyIndexDone;
    }

    /**
     * Generate history for single file.
     * @param filename name of the file
     * @param historyEntries list of HistoryEntry objects forming the (incremental) history of the file
     * @param env runtime environment
     * @param repository repository object in which the file belongs
     * @param srcFile file object
     * @param root root of the source repository
     * @param renamed true if the file was renamed in the past
     */
    private void doFileHistory(String filename, List<HistoryEntry> historyEntries,
            RuntimeEnvironment env, Repository repository,
            File srcFile, File root, boolean renamed) throws HistoryException {

        History hist = null;

        /*
         * If the file was renamed (in the changesets that are being indexed),
         * its history is not stored in the historyEntries so it needs to be acquired
         * directly from the repository.
         * This ensures that complete history of the file (across renames)
         * will be saved.
         */
        if (renamed) {
            hist = repository.getHistory(srcFile);
        }

        if (hist == null) {
            hist = new History();

            // File based history cache does not store files for individual
            // changesets so strip them.
            for (HistoryEntry ent : historyEntries) {
                ent.strip();
            }

            // add all history entries
            hist.setHistoryEntries(historyEntries);
        } else {
            for (HistoryEntry ent : hist.getHistoryEntries()) {
                ent.strip();
            }
        }

        // Assign tags to changesets they represent.
        if (env.isTagsEnabled() && repository.hasFileBasedTags()) {
            repository.assignTagsInHistory(hist);
        }

        File file = new File(root, filename);
        if (!file.isDirectory()) {
            storeFile(hist, file, repository, !renamed);
        }
    }

    private boolean isRenamedFile(String filename,
            RuntimeEnvironment env, 
            Repository repository, History history) throws IOException {

        String repodir = env.getPathRelativeToSourceRoot(
            new File(repository.getDirectoryName()), 0);
        String shortestfile = filename.substring(repodir.length() + 1);

        return (history.isRenamed(shortestfile));
    }

    static class FilePersistenceDelegate extends PersistenceDelegate {
        @Override
        protected Expression instantiate(Object oldInstance, Encoder out) {
            File f = (File)oldInstance;
            return new Expression(oldInstance, f.getClass(), "new",
                new Object[] {f.toString()});
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
            throw new HistoryException("Failed to get path relative to " +
                    "source root for " + file, e);
        }

        return new File(sb.toString());
    }

    /**
     * Read history from a file.
     */
    private static History readCache(File file) throws IOException {
        try {
            FileInputStream in = new FileInputStream(file);
            XMLDecoder d = new XMLDecoder(
                new BufferedInputStream(new GZIPInputStream(in)));
            return (History) d.readObject();
        } catch (IOException e) {
            throw e;
        }
    }

    /**
     * Store history in file on disk.
     * @param dir directory where the file will be saved
     * @param history history to store
     * @param cacheFile the file to store the history to
     * @throws HistoryException
     */
    private void writeHistoryToFile(File dir, History history, File cacheFile) throws HistoryException {
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
            try (FileOutputStream out = new FileOutputStream(output);
                    XMLEncoder e = new XMLEncoder(
                            new BufferedOutputStream(
                                    new GZIPOutputStream(out)))) {
                e.setPersistenceDelegate(File.class,
                        new FilePersistenceDelegate());
                e.writeObject(history);
            }
        } catch (IOException ioe) {
            throw new HistoryException("Failed to write history", ioe);
        }
        synchronized (lock) {
            if (!cacheFile.delete() && cacheFile.exists()) {
                if (!output.delete()) {
                    LOGGER.log(Level.WARNING,
                            "Failed to remove temporary history cache file");
                }
                throw new HistoryException(
                        "Cachefile exists, and I could not delete it.");
            }
            if (!output.renameTo(cacheFile)) {
                if (!output.delete()) {
                    LOGGER.log(Level.WARNING,
                            "Failed to remove temporary history cache file");
                }
                throw new HistoryException("Failed to rename cache tmpfile.");
            }
        }
    }

    /**
     * Read history from cacheFile and merge it with histNew, return merged history.
     *
     * @param cacheFile file to where the history object will be stored
     * @param histNew history object with new history entries
     * @param repo repository to where pre pre-image of the cacheFile belong
     * @return merged history (can be null if merge failed for some reason)
     * @throws HistoryException
     */
    private History mergeOldAndNewHistory(File cacheFile, History histNew, Repository repo)
            throws HistoryException {

        History histOld;
        History history = null;

        try {
            histOld = readCache(cacheFile);
            // Merge old history with the new history.
            List<HistoryEntry> listOld = histOld.getHistoryEntries();
            if (!listOld.isEmpty()) {
                RuntimeEnvironment env = RuntimeEnvironment.getInstance();
                List<HistoryEntry> listNew = histNew.getHistoryEntries();
                ListIterator li = listNew.listIterator(listNew.size());
                while (li.hasPrevious()) {
                    listOld.add(0, (HistoryEntry) li.previous());
                }
                history = new History(listOld);

                // Retag the last changesets in case there have been some new
                // tags added to the repository. Technically we should just
                // retag the last revision from the listOld however this
                // does not solve the problem when listNew contains new tags
                // retroactively tagging changesets from listOld so we resort
                // to this somewhat crude solution.
                if (env.isTagsEnabled() && repo.hasFileBasedTags()) {
                    for (HistoryEntry ent : history.getHistoryEntries()) {
                        ent.setTags(null);
                    }
                    repo.assignTagsInHistory(history);
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE,
                String.format("Cannot open history cache file %s", cacheFile.getPath()), ex);
        }

        return history;
    }

    /**
     * Store history object (encoded as XML and compressed with gzip) in a file.
     *
     * @param history history object to store
     * @param file file to store the history object into
     * @param repo repository for the file
     * @param mergeHistory whether to merge the history with existing or
     *                     store the histNew as is
     * @throws HistoryException
     */
    private void storeFile(History histNew, File file, Repository repo,
            boolean mergeHistory) throws HistoryException {

        File cacheFile = getCachedFile(file);
        History history = histNew;

        File dir = cacheFile.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new HistoryException(
                    "Unable to create cache directory '" + dir + "'.");
        }

        if (mergeHistory && cacheFile.exists()) {
            history = mergeOldAndNewHistory(cacheFile, histNew, repo);
        }

        // If the merge failed, null history will be returned.
        // In such case store at least new history as a best effort.
        if (history != null) {
            writeHistoryToFile(dir, history, cacheFile);
        } else {
            writeHistoryToFile(dir, histNew, cacheFile);
        }
    }

    private void storeFile(History histNew, File file, Repository repo)
            throws HistoryException {
        storeFile(histNew, file, repo, false);
    }

    private void finishStore(Repository repository, String latestRev) {
        storeLatestCachedRevision(repository, latestRev);
        LOGGER.log(Level.FINE,
            "Done storing history for repo {0}",
            new Object[] {repository.getDirectoryName()});
    }

    /**
     * Store history for the whole repository in directory hierarchy resembling
     * the original repository structure. History of individual files will be
     * stored under this hierarchy, each file containing history of
     * corresponding source file.
     *
     * @param history history object to process into per-file histories
     * @param repository repository object
     * @throws HistoryException
     */
    @Override
    public void store(History history, Repository repository)
            throws HistoryException {
        final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        String latestRev = null;

        // Return immediately when there is nothing to do.
        List<HistoryEntry> entries = history.getHistoryEntries();
        if (entries.isEmpty()) {
            return;
        }

        LOGGER.log(Level.FINE,
            "Storing history for repo {0}",
            new Object[] {repository.getDirectoryName()});

        HashMap<String, List<HistoryEntry>> map =
                new HashMap<>();

        /*
         * Go through all history entries for this repository (acquired through
         * history/log command executed for top-level directory of the repo
         * and parsed into HistoryEntry structures) and create hash map which
         * maps file names into list of HistoryEntry structures corresponding
         * to changesets in which the file was modified.
         */
        for (HistoryEntry e : history.getHistoryEntries()) {
            // The history entries are sorted from newest to oldest.
            if (latestRev == null) {
                latestRev = e.getRevision();
            }
            for (String s : e.getFiles()) {
                /*
                 * We do not want to generate history cache for files which
                 * do not currently exist in the repository.
                 */
                File test = new File(env.getSourceRootPath() + s);
                if (!test.exists()) {
                    continue;
                }

                List<HistoryEntry> list = map.get(s);
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(s, list);
                }
                /*
                 * We need to do deep copy in order to have different tags
                 * per each commit.
                 */
                if (env.isTagsEnabled() && repository.hasFileBasedTags()) {
                    list.add(new HistoryEntry(e));
                } else {
                    list.add(e);
                }
            }
        }

        /*
         * Now traverse the list of files from the hash map built above
         * and for each file store its history (saved in the value of the
         * hash map entry for the file) in a file. Skip renamed files
         * which will be handled separately below.
         */
        final File root = RuntimeEnvironment.getInstance().getSourceRootFile();
        for (Map.Entry<String, List<HistoryEntry>> map_entry : map.entrySet()) {
            try {
                if (env.isHandleHistoryOfRenamedFiles() &&
                    isRenamedFile(map_entry.getKey(), env, repository, history)) {
                        continue;
                }
            } catch (IOException ex) {
               LOGGER.log(Level.WARNING,
                   "isRenamedFile() got exception " , ex);
            }

            doFileHistory(map_entry.getKey(), map_entry.getValue(),
                env, repository, null, root, false);
        }

        if (!env.isHandleHistoryOfRenamedFiles()) {
            finishStore(repository, latestRev);
            return;
        }

        /*
         * Now handle renamed files (in parallel).
         */
        HashMap<String, List<HistoryEntry>> renamed_map =
                new HashMap<>();
        for (final Map.Entry<String, List<HistoryEntry>> map_entry : map.entrySet()) {
            try {
                if (isRenamedFile(map_entry.getKey(), env, repository, history)) {
                    renamed_map.put(map_entry.getKey(), map_entry.getValue());
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING,
                    "isRenamedFile() got exception ", ex);
            }
        }
        // The directories for the renamed files have to be created before
        // the actual files otherwise storeFile() might be racing for
        // mkdirs() if there are multiple renamed files from single directory
        // handled in parallel.
        for (final String file : renamed_map.keySet()) {
            File cache = getCachedFile(new File(env.getSourceRootPath() + file));
            File dir = cache.getParentFile();

            if (!dir.isDirectory() && !dir.mkdirs()) {
                LOGGER.log(Level.WARNING,
                   "Unable to create cache directory ' {0} '.", dir);
            }
        }
        final Repository repositoryF = repository;
        final CountDownLatch latch = new CountDownLatch(renamed_map.size());
        for (final Map.Entry<String, List<HistoryEntry>> map_entry : renamed_map.entrySet()) {
            RuntimeEnvironment.getHistoryRenamedExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        doFileHistory(map_entry.getKey(), map_entry.getValue(),
                            env, repositoryF,
                            new File(env.getSourceRootPath() + map_entry.getKey()),
                            root, true);
                    } catch (Exception ex) {
                        // We want to catch any exception since we are in thread.
                        LOGGER.log(Level.WARNING,
                            "doFileHistory() got exception ", ex);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        // Wait for the executors to finish.
        try {
            // Wait for the executors to finish.
            latch.await();
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "latch exception ",ex);
        }
        finishStore(repository, latestRev);
    }

    @Override
    public History get(File file, Repository repository, boolean withFiles)
            throws HistoryException {
        File cache = getCachedFile(file);
        if (isUpToDate(file, cache)) {
            try {
                return readCache(cache);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Error when reading cache file '" + cache, e);
            }
        }

        /*
         * Some mirrors of repositories which are capable of fetching history
         * for directories may contain lots of files untracked by given SCM.
         * For these it would be waste of time to get their history
         * since the history of all files in this repository should have been
         * fetched in the first phase of indexing.
         */
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (isHistoryIndexDone() && repository.hasHistoryForDirectories() &&
            !env.isFetchHistoryWhenNotInCache()) {
                return null;
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
            if ((cache != null) &&
                        (cache.exists() ||
                             (time > env.getHistoryReaderTimeLimit()))) {
                // retrieving the history takes too long, cache it!
                storeFile(history, file, repository);
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
        dir = new File(dir, this.historyCacheDirName);
        try {
            dir = new File(dir, env.getPathRelativeToSourceRoot(
                new File(repos.getDirectoryName()), 0));
        } catch (IOException e) {
            throw new HistoryException("Could not resolve " +
                    repos.getDirectoryName()+" relative to source root", e);
        }
        return dir.exists();
    }

    public String getRepositoryHistDataDirname(Repository repository) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String repoDirBasename;

        try {
            repoDirBasename = env.getPathRelativeToSourceRoot(
                    new File(repository.getDirectoryName()), 0);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Could not resolve " +
                repository.getDirectoryName()+" relative to source root", ex);
            return null;
        }

        return env.getDataRootPath() + File.separatorChar
            + this.historyCacheDirName
            + repoDirBasename;
    }

    private String getRepositoryCachedRevPath(Repository repository) {
        return getRepositoryHistDataDirname(repository)
            + File.separatorChar + latestRevFileName;
    }

    /**
     * Store latest indexed revision for the repository under data directory.
     * @param repository repository
     * @param rev latest revision which has been just indexed
     */
    private void storeLatestCachedRevision(Repository repository, String rev) {
        Writer writer = null;

        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                  new FileOutputStream(getRepositoryCachedRevPath(repository))));
            writer.write(rev);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Cannot write latest cached revision to file for "+repository.getDirectoryName(),
                ex);
        } finally {
           try {
               if (writer != null) {
                   writer.close();
               }
           } catch (IOException ex) {
               LOGGER.log(Level.FINEST, "Cannot close file", ex);
           }
        }
    }

    @Override
    public String getLatestCachedRevision(Repository repository) {
        String rev = null;
        BufferedReader input;

        try {
            input = new BufferedReader(new FileReader(getRepositoryCachedRevPath(repository)));
            try {
                rev = input.readLine();
            } catch (java.io.IOException e) {
                LOGGER.log(Level.WARNING, "failed to load ", e);
                return null;
            } finally {
                try {
                    input.close();
                } catch (java.io.IOException e) {
                    LOGGER.log(Level.FINEST, "failed to close", e);
                }
            }
        } catch (java.io.FileNotFoundException e) {
            LOGGER.log(Level.FINE, "not loading latest cached revision file from {0}",
                getRepositoryCachedRevPath(repository));
            return null;
        }

        return rev;
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
        // remove the file cached last revision (done separately in case
        // it gets ever moved outside of the hierarchy)
        File cachedRevFile = new File(getRepositoryCachedRevPath(repository));
        cachedRevFile.delete();

        // Remove all files which constitute the history cache.
        try {
            IOUtils.removeRecursive(Paths.get(getRepositoryHistDataDirname(repository)));
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public String getInfo() {
        return getClass().getSimpleName();
    }
}
