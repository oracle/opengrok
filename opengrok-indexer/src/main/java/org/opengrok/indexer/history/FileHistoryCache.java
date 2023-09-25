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
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.SmileParser;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.Nullable;
import org.opengrok.indexer.Metrics;
import org.opengrok.indexer.configuration.PathAccepter;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.DirectoryEntry;
import org.opengrok.indexer.util.Progress;
import org.opengrok.indexer.util.Statistics;


/**
 * Class representing file based storage of per source file history.
 */
class FileHistoryCache extends AbstractCache implements HistoryCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileHistoryCache.class);
    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static final String HISTORY_CACHE_DIR_NAME = "historycache";
    private static final String LATEST_REV_FILE_NAME = "OpenGroklatestRev";

    private final PathAccepter pathAccepter = env.getPathAccepter();

    private Counter fileHistoryCacheHits;
    private Counter fileHistoryCacheMisses;

    /**
     * Generate history cache for single renamed file.
     * @param filename file path
     * @param repository repository
     * @param root root
     * @param tillRevision end revision (can be null)
     */
    public void doRenamedFileHistory(String filename, File file, Repository repository, File root, String tillRevision)
            throws HistoryException {

        History history;

        if (tillRevision != null) {
            if (!(repository instanceof RepositoryWithPerPartesHistory)) {
                throw new RuntimeException("cannot use non null tillRevision on repository");
            }

            RepositoryWithPerPartesHistory repo = (RepositoryWithPerPartesHistory) repository;
            history = repo.getHistory(file, null, tillRevision);
        } else {
            history = repository.getHistory(file);
        }

        history.strip();
        doFileHistory(filename, history, repository, root, true);
    }

    /**
     * Generate history cache for single file.
     * @param filename name of the file
     * @param history history
     * @param repository repository object in which the file belongs
     * @param root root of the source repository
     * @param renamed true if the file was renamed in the past
     */
    private void doFileHistory(String filename, History history, Repository repository, File root, boolean renamed)
            throws HistoryException {

        File file = new File(root, filename);
        if (file.isDirectory()) {
            return;
        }

        // Assign tags to changesets they represent.
        if (env.isTagsEnabled() && repository.hasFileBasedTags()) {
            repository.assignTagsInHistory(history);
        }

        storeFile(history, file, repository, !renamed);
    }

    @Override
    public void initialize() {
        MeterRegistry meterRegistry = Metrics.getRegistry();
        if (meterRegistry != null) {
            fileHistoryCacheHits = Counter.builder("cache.history.file.get").
                    description("file history cache hits").
                    tag("what", "hits").
                    register(meterRegistry);
            fileHistoryCacheMisses = Counter.builder("cache.history.file.get").
                    description("file history cache misses").
                    tag("what", "miss").
                    register(meterRegistry);
        }
    }

    double getFileHistoryCacheHits() {
        return fileHistoryCacheHits.count();
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
     * Read complete history from the cache.
     */
    static History readHistory(File cacheFile, Repository repository) throws IOException {
        SmileFactory factory = new SmileFactory();
        ObjectMapper mapper = new SmileMapper();
        List<HistoryEntry> historyEntryList = new ArrayList<>();

        try (SmileParser parser = factory.createParser(cacheFile)) {
            parser.setCodec(mapper);
            Iterator<HistoryEntry> historyEntryIterator = parser.readValuesAs(HistoryEntry.class);
            historyEntryIterator.forEachRemaining(historyEntryList::add);
        }

        History history = new History(historyEntryList);

        // Read tags from separate file.
        if (env.isTagsEnabled() && repository.hasFileBasedTags()) {
            File tagFile = getTagsFile(cacheFile);
            try (SmileParser parser = factory.createParser(tagFile)) {
                parser.setCodec(mapper);
                Map<String, String> tags = parser.readValueAs(new TypeReference<HashMap<String, String>>() {
                });
                history.setTags(tags);
            } catch (IOException ioe) {
                // Handle the exception here gracefully - it impacts the history only partially.
                LOGGER.log(Level.WARNING, "failed to read tags from ''{0}''", tagFile);
            }
        }

        return history;
    }

    static HistoryEntry readLastHistoryEntry(File cacheFile) throws IOException {
        SmileFactory factory = new SmileFactory();
        ObjectMapper mapper = new SmileMapper();
        HistoryEntry historyEntry = null;

        try (SmileParser parser = factory.createParser(cacheFile)) {
            parser.setCodec(mapper);
            Iterator<HistoryEntry> historyEntryIterator = parser.readValuesAs(HistoryEntry.class);
            if (historyEntryIterator.hasNext()) {
                historyEntry = historyEntryIterator.next();
            }
        }

        return historyEntry;
    }

    /**
     * Write serialized object to file.
     * @param history {@link History} instance to be stored
     * @param outputFile output file
     * @throws IOException on error
     */
    public static void writeHistoryTo(History history, File outputFile) throws IOException {
        ObjectWriter objectWriter = getObjectWriter();

        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            for (HistoryEntry historyEntry : history.getHistoryEntries()) {
                byte[] bytes = objectWriter.writeValueAsBytes(historyEntry);
                outputStream.write(bytes);
            }
        }
    }

    public static void writeTagsTo(File outputFile, History history) throws IOException {
        SmileFactory smileFactory = new SmileFactory();
        // need header to enable shared string values
        smileFactory.configure(SmileGenerator.Feature.WRITE_HEADER, true);
        smileFactory.configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES, false);

        ObjectMapper mapper = new SmileMapper(smileFactory);
        // ObjectMapper mapper = new JsonMapper();
        ObjectWriter objectWriter = mapper.writer().forType(HashMap.class);

        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            byte[] bytes = objectWriter.writeValueAsBytes(history.getTags());
            outputStream.write(bytes);
        }
    }

    private static ObjectWriter getObjectWriter() {
        SmileFactory smileFactory = new SmileFactory();
        // need header to enable shared string values
        smileFactory.configure(SmileGenerator.Feature.WRITE_HEADER, true);
        smileFactory.configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES, false);

        ObjectMapper mapper = new SmileMapper(smileFactory);
        // ObjectMapper mapper = new JsonMapper();
        return mapper.writer().forType(HistoryEntry.class);
    }

    private void safelyRename(File output, File cacheFile) throws HistoryException {
        if (!cacheFile.delete() && cacheFile.exists()) {
            if (!output.delete()) {
                LOGGER.log(Level.WARNING, "Failed to remove temporary cache file ''{0}''", cacheFile);
            }
            throw new HistoryException(String.format("Cache file '%s' exists, and could not be deleted.",
                    cacheFile));
        }
        if (!output.renameTo(cacheFile)) {
            try {
                Files.delete(output.toPath());
            } catch (IOException e) {
                throw new HistoryException("failed to delete output file", e);
            }
            throw new HistoryException(String.format("Failed to rename cache temporary file '%s' to '%s'",
                    output, cacheFile));
        }
    }

    private static File getTagsFile(File file) {
        return new File(file.getAbsolutePath() + ".t");
    }

    @Override
    public void storeFile(History history, File file, Repository repository) throws HistoryException {
        storeFile(history, file, repository, false);
    }

    /**
     * Store {@link History} object in a file.
     *
     * @param histNew history object to store
     * @param file file to store the history object into
     * @param repo repository for the file
     * @param mergeHistory whether to merge the history with existing or store the histNew as is
     * @throws HistoryException if there was any problem with history cache generation
     */
    private void storeFile(History histNew, File file, Repository repo, boolean mergeHistory) throws HistoryException {
        File cacheFile;
        try {
            cacheFile = getCachedFile(file);
        } catch (CacheException e) {
            throw new HistoryException(e);
        }

        File dir = cacheFile.getParentFile();
        // calling isDirectory twice to prevent a race condition
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new HistoryException("Unable to create cache directory '" + dir + "'.");
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "writing history entries to ''{0}'': {1}",
                    new Object[]{cacheFile, histNew.getRevisionList()});
        }

        final File outputFile;
        try {
            outputFile = File.createTempFile("ogtmp", null, dir);
            writeHistoryTo(histNew, outputFile);
        } catch (IOException ioe) {
            throw new HistoryException("Failed to write history", ioe);
        }

        boolean assignTags = env.isTagsEnabled() && repo.hasFileBasedTags();

        // Append the contents of the pre-existing cache file to the temporary file.
        if (mergeHistory && cacheFile.exists()) {
            try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile, true))) {
                SmileFactory factory = new SmileFactory();
                ObjectMapper mapper = new SmileMapper();
                ObjectWriter objectWriter = getObjectWriter();
                try (SmileParser parser = factory.createParser(cacheFile)) {
                    parser.setCodec(mapper);
                    Iterator<HistoryEntry> historyEntryIterator = parser.readValuesAs(HistoryEntry.class);
                    while (historyEntryIterator.hasNext()) {
                        HistoryEntry historyEntry = historyEntryIterator.next();
                        byte[] bytes = objectWriter.writeValueAsBytes(historyEntry);
                        outputStream.write(bytes);
                        if (assignTags) {
                            histNew.getHistoryEntries().add(historyEntry);
                        }
                    }
                }
            } catch (IOException ioe) {
                throw new HistoryException("Failed to write history", ioe);
            }

            // Re-tag the changesets in case there have been some new
            // tags added to the repository. Technically we should just
            // re-tag the last revision from the listOld however this
            // does not solve the problem when listNew contains new tags
            // retroactively tagging changesets from listOld, so we resort
            // to this somewhat crude solution of re-tagging from scratch.
            if (assignTags) {
                histNew.strip();
                repo.assignTagsInHistory(histNew);
            }
        }

        // Generate the file with a temporary name and move it into place when
        // done, so it is not necessary to protect the readers for partially updated
        // files.
        if (assignTags) {
            // Store tags in separate file.
            // Ideally that should be done using the cycle above to avoid dealing with complete History instance.
            File outputTagsFile = getTagsFile(outputFile);
            try {
                writeTagsTo(outputTagsFile, histNew);
            } catch (IOException ioe) {
                throw new HistoryException("Failed to write tags", ioe);
            }

            safelyRename(outputTagsFile, getTagsFile(cacheFile));
        }

        safelyRename(outputFile, cacheFile);
    }

    private void finishStore(Repository repository, String latestRev) {
        String histDir = CacheUtil.getRepositoryCacheDataDirname(repository, this);
        if (histDir == null || !(new File(histDir)).isDirectory()) {
            // If the history was not created for some reason (e.g. temporary
            // failure), do not create the CachedRevision file as this would
            // create confusion (once it starts working again).
            LOGGER.log(Level.WARNING,
                "Could not store history for repository {0}: ''{1}'' is not a directory",
                new Object[]{repository, histDir});
        } else {
            storeLatestCachedRevision(repository, latestRev);
        }
    }

    @Override
    public void store(History history, Repository repository) throws CacheException {
        store(history, repository, null);
    }

    /**
     * Go through history entries for this repository acquired through
     * history/log command executed for top-level directory of the repo
     * and parsed into {@link HistoryEntry} structures and create hash map which
     * maps file names into list of HistoryEntry structures corresponding
     * to changesets in which the file was modified.
     * @return latest revision
     */
    private String createFileMap(History history, HashMap<String, List<HistoryEntry>> map) {
        String latestRev = null;
        HashMap<String, Boolean> acceptanceCache = new HashMap<>();

        for (HistoryEntry e : history.getHistoryEntries()) {
            // The history entries are sorted from newest to oldest.
            if (latestRev == null) {
                latestRev = e.getRevision();
            }
            for (String s : e.getFiles()) {
                /*
                 * We do not want to generate history cache for files which
                 * do not currently exist in the repository.
                 *
                 * Also, we cache the result of this evaluation to boost
                 * performance, since a particular file can appear in many
                 * repository revisions.
                 */
                File test = new File(env.getSourceRootPath() + s);
                String testKey = test.getAbsolutePath();
                Boolean cachedAcceptance = acceptanceCache.get(testKey);
                if (cachedAcceptance != null) {
                    if (!cachedAcceptance) {
                        continue;
                    }
                } else {
                    boolean testResult = test.exists() && pathAccepter.accept(test);
                    acceptanceCache.put(testKey, testResult);
                    if (!testResult) {
                        continue;
                    }
                }

                List<HistoryEntry> list = map.computeIfAbsent(s, k -> new ArrayList<>());

                list.add(e);
            }
        }
        return latestRev;
    }

    private static String getRevisionString(String revision) {
        if (revision == null) {
            return "end of history";
        } else {
            return "revision " + revision;
        }
    }

    /**
     * Store history for the whole repository in directory hierarchy resembling
     * the original repository structure. History of individual files will be
     * stored under this hierarchy, each file containing history of
     * corresponding source file.
     *
     * <p>
     * <b>Note that the history object will be changed in the process of storing the history into cache.
     * Namely the list of files from the history entries will be stripped.</b>
     * </p>
     *
     * @param history history object to process into per-file histories
     * @param repository repository object
     * @param tillRevision end revision (can be null)
     */
    @Override
    public void store(History history, Repository repository, String tillRevision) throws CacheException {

        final boolean handleRenamedFiles = repository.isHandleRenamedFiles();

        String latestRev = null;

        // Return immediately when there is nothing to do.
        List<HistoryEntry> entries = history.getHistoryEntries();
        if (entries.isEmpty()) {
            return;
        }

        HashMap<String, List<HistoryEntry>> map = new HashMap<>();
        String fileMapLatestRev = createFileMap(history, map);
        if (history.getLatestRev() != null) {
            latestRev = history.getLatestRev();
        } else {
            latestRev = fileMapLatestRev;
        }

        // File based history cache does not store files for individual changesets so strip them.
        history.strip();

        String repoCachePath = CacheUtil.getRepositoryCacheDataDirname(repository, this);
        if (repoCachePath == null) {
            throw new CacheException(String.format("failed to get cache directory path for %s", repository));
        }
        File histDataDir = new File(repoCachePath);
        // Check the directory again in case of races (might happen in the presence of sub-repositories).
        if (!histDataDir.isDirectory() && !histDataDir.mkdirs() && !histDataDir.isDirectory()) {
            throw new CacheException(String.format("cannot create history cache directory for '%s'", histDataDir));
        }

        Set<String> regularFiles = map.keySet().stream().
                filter(e -> !history.isRenamed(e)).collect(Collectors.toSet());
        createDirectoriesForFiles(regularFiles, repository, "regular files for history till " +
                getRevisionString(tillRevision));

        /*
         * Now traverse the list of files from the hash map built above and for each file store its history
         * (saved in the value of the hash map entry for the file) in a file.
         * The renamed files will be handled separately.
         */
        Level logLevel = Level.FINE;
        LOGGER.log(logLevel, "Storing history for {0} regular files in repository {1} till {2}",
                new Object[]{regularFiles.size(), repository, getRevisionString(tillRevision)});
        final File root = env.getSourceRootFile();

        final CountDownLatch latch = new CountDownLatch(regularFiles.size());
        AtomicInteger fileHistoryCount = new AtomicInteger();
        try (Progress progress = new Progress(LOGGER,
                String.format("history cache for regular files of %s till %s", repository,
                        getRevisionString(tillRevision)),
                regularFiles.size(), logLevel)) {
            for (String file : regularFiles) {
                env.getIndexerParallelizer().getHistoryFileExecutor().submit(() -> {
                    try {
                        doFileHistory(file, new History(map.get(file)), repository, root, false);
                        fileHistoryCount.getAndIncrement();
                    } catch (Exception ex) {
                        // We want to catch any exception since we are in a thread.
                        LOGGER.log(Level.WARNING, "doFileHistory() got exception ", ex);
                    } finally {
                        latch.countDown();
                        progress.increment();
                    }
                });
            }

            // Wait for the executors to finish.
            try {
                latch.await();
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "latch exception", ex);
            }
            LOGGER.log(logLevel, "Stored history for {0} regular files in repository {1}",
                    new Object[]{fileHistoryCount, repository});
        }

        if (!handleRenamedFiles) {
            finishStore(repository, latestRev);
            return;
        }

        storeRenamed(history.getRenamedFiles(), repository, tillRevision);

        finishStore(repository, latestRev);
    }

    /**
     * handle renamed files (in parallel).
     * @param renamedFiles set of renamed file paths
     * @param repository repository
     * @param tillRevision end revision (can be null)
     */
    public void storeRenamed(Set<String> renamedFiles, Repository repository, String tillRevision) throws CacheException {
        final File root = env.getSourceRootFile();
        if (renamedFiles.isEmpty()) {
            return;
        }

        renamedFiles = renamedFiles.stream().filter(f -> new File(env.getSourceRootPath() + f).exists()).
                collect(Collectors.toSet());
        Level logLevel = Level.FINE;
        LOGGER.log(logLevel, "Storing history for {0} renamed files in repository {1} till {2}",
                new Object[]{renamedFiles.size(), repository, getRevisionString(tillRevision)});

        createDirectoriesForFiles(renamedFiles, repository, "renamed files for history " +
                getRevisionString(tillRevision));

        final Repository repositoryF = repository;
        final CountDownLatch latch = new CountDownLatch(renamedFiles.size());
        AtomicInteger renamedFileHistoryCount = new AtomicInteger();
        try (Progress progress = new Progress(LOGGER,
                String.format("history cache for renamed files of %s till %s", repository,
                        getRevisionString(tillRevision)),
                renamedFiles.size(), logLevel)) {
            for (final String file : renamedFiles) {
                env.getIndexerParallelizer().getHistoryFileExecutor().submit(() -> {
                    try {
                        doRenamedFileHistory(file,
                                new File(env.getSourceRootPath() + file),
                                repositoryF, root, tillRevision);
                        renamedFileHistoryCount.getAndIncrement();
                    } catch (Exception ex) {
                        // We want to catch any exception since we are in thread.
                        LOGGER.log(Level.WARNING, "doFileHistory() got exception ", ex);
                    } finally {
                        latch.countDown();
                        progress.increment();
                    }
                });
            }

            // Wait for the executors to finish.
            try {
                // Wait for the executors to finish.
                latch.await();
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "latch exception", ex);
            }
        }
        LOGGER.log(logLevel, "Stored history for {0} renamed files in repository {1}",
                new Object[]{renamedFileHistoryCount.intValue(), repository});
    }

    private void createDirectoriesForFiles(Set<String> files, Repository repository, String label) {

        // The directories for the files have to be created before
        // the actual files otherwise storeFile() might be racing for
        // mkdirs() if there are multiple files from single directory
        // handled in parallel.
        Statistics elapsed = new Statistics();
        LOGGER.log(Level.FINE, "Starting directory creation for {0} ({1}): {2} directories",
                new Object[]{repository, label, files.size()});
        for (final String file : files) {
            File cache;
            try {
                cache = getCachedFile(new File(env.getSourceRootPath() + file));
            } catch (CacheException ex) {
                LOGGER.log(Level.FINER, ex.getMessage());
                continue;
            }
            File dir = cache.getParentFile();

            if (!dir.isDirectory() && !dir.mkdirs()) {
                LOGGER.log(Level.WARNING, "Unable to create cache directory ''{0}''", dir);
            }
        }
        elapsed.report(LOGGER, Level.FINE, String.format("Done creating directories for %s (%s)", repository, label));
    }

    @Override
    public History get(File file, Repository repository, boolean withFiles) throws CacheException {

        if (file.isDirectory()) {
            return null;
        }

        if (isUpToDate(file)) {
            File cacheFile = getCachedFile(file);
            try {
                if (fileHistoryCacheHits != null) {
                    fileHistoryCacheHits.increment();
                }
                return readHistory(cacheFile, repository);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, String.format("Error when reading cache file '%s'", cacheFile), e);
            }
        }

        if (fileHistoryCacheMisses != null) {
            fileHistoryCacheMisses.increment();
        }

        return null;
    }

    @Override
    @Nullable
    public HistoryEntry getLastHistoryEntry(File file) throws CacheException {
        if (isUpToDate(file)) {
            File cacheFile = getCachedFile(file);
            try {
                if (fileHistoryCacheHits != null) {
                    fileHistoryCacheHits.increment();
                }
                return readLastHistoryEntry(cacheFile);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, String.format("Error when reading cache file '%s'", cacheFile), e);
            }
        }

        if (fileHistoryCacheMisses != null) {
            fileHistoryCacheMisses.increment();
        }

        return null;
    }

    /**
     * @param file the file to check
     * @return {@code true} if the cache is up-to-date for the file, {@code false} otherwise
     */
    public boolean isUpToDate(File file) throws CacheException {
        File cachedFile = getCachedFile(file);
        return cachedFile != null && cachedFile.exists() && file.lastModified() <= cachedFile.lastModified();
    }

    private String getRepositoryCachedRevPath(RepositoryInfo repository) {
        String histDir = CacheUtil.getRepositoryCacheDataDirname(repository, this);
        if (histDir == null) {
            return null;
        }
        return histDir + File.separatorChar + LATEST_REV_FILE_NAME;
    }

    /**
     * Store latest indexed revision for the repository under data directory.
     * @param repository repository
     * @param rev latest revision which has been just indexed
     */
    private void storeLatestCachedRevision(Repository repository, String rev) {
        Path newPath = Path.of(getRepositoryCachedRevPath(repository));
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(newPath.toFile())))) {
            writer.write(rev);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING,
                    String.format("Cannot write latest cached revision to file for repository %s", repository), ex);
        }
    }

    @Override
    @Nullable
    public String getLatestCachedRevision(Repository repository) {
        return getCachedRevision(repository, getRepositoryCachedRevPath(repository));
    }

    @Nullable
    private String getCachedRevision(Repository repository, String revPath) {
        String rev;
        BufferedReader input;

        if (revPath == null) {
            LOGGER.log(Level.WARNING, "no rev path for repository {0}", repository);
            return null;
        }

        try {
            input = new BufferedReader(new FileReader(revPath));
            try {
                rev = input.readLine();
            } catch (java.io.IOException e) {
                LOGGER.log(Level.WARNING, "failed to load ", e);
                return null;
            } finally {
                try {
                    input.close();
                } catch (java.io.IOException e) {
                    LOGGER.log(Level.WARNING, "failed to close", e);
                }
            }
        } catch (java.io.FileNotFoundException e) {
            LOGGER.log(Level.FINE,
                "not loading latest cached revision file from {0}", revPath);
            return null;
        }

        return rev;
    }

    @Override
    public boolean fillLastHistoryEntries(List<DirectoryEntry> entries) {
        if (entries == null) {
            return false;
        }

        boolean ret = true;

        Statistics statistics = new Statistics();

        final ExecutorService executor = env.getDirectoryListingExecutor();
        Set<Future<Boolean>> futures = new HashSet<>();
        for (DirectoryEntry directoryEntry : entries) {
            futures.add(executor.submit(() -> {
                try {
                    File file = directoryEntry.getFile();
                    if (file.isDirectory()) {
                        directoryEntry.setDescription("-");
                        directoryEntry.setDate(null);
                        return true;
                    }

                    HistoryEntry historyEntry = getLastHistoryEntry(file);
                    if (historyEntry != null && historyEntry.getDate() != null) {
                        directoryEntry.setDescription(historyEntry.getDescription());
                        directoryEntry.setDate(historyEntry.getDate());
                    } else {
                        LOGGER.log(Level.FINE, "cannot get last history entry for ''{0}''",
                                directoryEntry.getFile());
                        return false;
                    }
                } catch (CacheException e) {
                    LOGGER.log(Level.FINER, "cannot get last history entry for ''{0}''", directoryEntry.getFile());
                    return false;
                }
                return true;
            }));
        }

        for (Future<Boolean> future : futures) {
            try {
                if (!future.get()) {
                    ret = false;
                    break;
                }
            } catch (Exception e) {
                ret = false;
                break;
            }
        }

        statistics.report(LOGGER, Level.FINER, "done filling directory entries");

        // Enforce the all-or-nothing semantics.
        if (!ret) {
            entries.forEach(e -> e.setDate(null));
            entries.forEach(e -> e.setDescription(null));
        }

        return ret;
    }

    @Override
    public void clear(RepositoryInfo repository) {
        String revPath = getRepositoryCachedRevPath(repository);
        if (revPath != null) {
            // remove the file cached last revision (done separately in case
            // it gets ever moved outside the hierarchy)
            File cachedRevFile = new File(revPath);
            try {
                Files.delete(cachedRevFile.toPath());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, String.format("failed to delete '%s'", cachedRevFile), e);
            }
        }

        CacheUtil.clearCacheDir(repository, this);
    }

    @Override
    public String getInfo() {
        return getClass().getSimpleName();
    }

    @Override
    public String getCacheDirName() {
        return HISTORY_CACHE_DIR_NAME;
    }
}
