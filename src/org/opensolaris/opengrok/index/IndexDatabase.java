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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.index;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.NoLockFactory;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.analysis.Ctags;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryException;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.web.Util;

/**
 * This class is used to create / update the index databases. Currently we use
 * one index database per project. 
 * 
 * @author Trond Norbye
 * @author Lubos Kosco , update for lucene 3.0.0
 */
public class IndexDatabase {

    private Project project;
    private FSDirectory indexDirectory;
    private FSDirectory spellDirectory;
    private IndexWriter writer;
    private TermEnum uidIter;
    private IgnoredNames ignoredNames;
    private Filter includedNames;
    private AnalyzerGuru analyzerGuru;
    private File xrefDir;
    private boolean interrupted;
    private List<IndexChangedListener> listeners;
    private File dirtyFile;
    private final Object lock = new Object();
    private boolean dirty;
    private boolean running;
    private List<String> directories;
    private static final Logger log = Logger.getLogger(IndexDatabase.class.getName());
    private Ctags ctags;
    private LockFactory lockfact;

    /**
     * Create a new instance of the Index Database. Use this constructor if
     * you don't use any projects
     * 
     * @throws java.io.IOException if an error occurs while creating directories
     */
    public IndexDatabase() throws IOException {
        this(null);        
    }

    /**
     * Create a new instance of an Index Database for a given project
     * @param project the project to create the database for
     * @throws java.io.IOException if an errror occurs while creating directories
     */
    public IndexDatabase(Project project) throws IOException {        
        this.project = project;
        lockfact = new SimpleFSLockFactory();
        initialize();
    }

    /**
     * Update the index database for all of the projects. Print progress to
     * standard out.
     * @param executor An executor to run the job
     * @throws IOException if an error occurs
     */
    public static void updateAll(ExecutorService executor) throws IOException {
        updateAll(executor, null);
    }

    /**
     * Update the index database for all of the projects
     * @param executor An executor to run the job
     * @param listener where to signal the changes to the database
     * @throws IOException if an error occurs
     */
    static void updateAll(ExecutorService executor, IndexChangedListener listener) throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        List<IndexDatabase> dbs = new ArrayList<IndexDatabase>();
        
        if (env.hasProjects()) {
            for (Project project : env.getProjects()) {
                dbs.add(new IndexDatabase(project));
            }
        } else {
            dbs.add(new IndexDatabase());
        }
        
        for (IndexDatabase d : dbs) {
            final IndexDatabase db = d;
            if (listener != null) {
                db.addIndexChangedListener(listener);
            }
            
            executor.submit(new Runnable() {

                @Override
                public void run() {
                    try {
                        db.update();
                    } catch (Exception e) {
                        log.log(Level.FINE,"Problem updating lucene index database: ",e);
                    }
                }
            });
        }
    }

    /**
     * Update the index database for a number of sub-directories
     * @param executor An executor to run the job
     * @param listener where to signal the changes to the database
     * @param paths
     * @throws IOException if an error occurs
     */
    public static void update(ExecutorService executor, IndexChangedListener listener, List<String> paths) throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        List<IndexDatabase> dbs = new ArrayList<IndexDatabase>();

        for (String path : paths) {
            Project project = Project.getProject(path);
            if (project == null && env.hasProjects()) {
                log.log(Level.WARNING, "Could not find a project for \"{0}\"", path);
            } else {
                IndexDatabase db;

                try {
                    if (project == null) {
                        db = new IndexDatabase();
                    } else {
                        db = new IndexDatabase(project);
                    }

                    int idx = dbs.indexOf(db);
                    if (idx != -1) {
                        db = dbs.get(idx);
                    }

                    if (db.addDirectory(path)) {
                        if (idx == -1) {
                            dbs.add(db);
                        }
                    } else {
                        log.log(Level.WARNING, "Directory does not exist \"{0}\"", path);
                    }
                } catch (IOException e) {
                    log.log(Level.WARNING, "An error occured while updating index", e);

                }
            }

            for (final IndexDatabase db : dbs) {
                db.addIndexChangedListener(listener);
                executor.submit(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            db.update();
                        } catch (Exception e) {
                            log.log(Level.WARNING, "An error occured while updating index", e);
                        }
                    }
                });
            }
        }
    }

    @SuppressWarnings("PMD.CollapsibleIfStatements")
    private void initialize() throws IOException {
        synchronized (this) {
            RuntimeEnvironment env = RuntimeEnvironment.getInstance();
            File indexDir = new File(env.getDataRootFile(), "index");
            File spellDir = new File(env.getDataRootFile(), "spellIndex");
            if (project != null) {
                indexDir = new File(indexDir, project.getPath());
                spellDir = new File(spellDir, project.getPath());
            }

            if (!indexDir.exists() && !indexDir.mkdirs()) {
                // to avoid race conditions, just recheck..
                if (!indexDir.exists()) {
                    throw new FileNotFoundException("Failed to create root directory [" + indexDir.getAbsolutePath() + "]");
                }
            }

            if (!spellDir.exists() && !spellDir.mkdirs()) {
                if (!spellDir.exists()) {
                    throw new FileNotFoundException("Failed to create root directory [" + spellDir.getAbsolutePath() + "]");
                }
            }
            
            if (!env.isUsingLuceneLocking()) {
                 lockfact = NoLockFactory.getNoLockFactory();
            }
            indexDirectory = FSDirectory.open(indexDir,lockfact);
            spellDirectory = FSDirectory.open(spellDir,lockfact);
            ignoredNames = env.getIgnoredNames();
            includedNames = env.getIncludedNames();
            analyzerGuru = new AnalyzerGuru();
            if (env.isGenerateHtml()) {
                xrefDir = new File(env.getDataRootFile(), "xref");
            }
            listeners = new ArrayList<IndexChangedListener>();
            dirtyFile = new File(indexDir, "dirty");
            dirty = dirtyFile.exists();
            directories = new ArrayList<String>();
        }
    }

    /**
     * By default the indexer will traverse all directories in the project.
     * If you add directories with this function update will just process
     * the specified directories.
     * 
     * @param dir The directory to scan
     * @return <code>true</code> if the file is added, false oth
     */
    @SuppressWarnings("PMD.UseStringBufferForStringAppends")
    public boolean addDirectory(String dir) {
        String directory = dir;
        if (directory.startsWith("\\")) {
            directory = directory.replace('\\', '/');
        } else if (directory.charAt(0) != '/') {
            directory = "/" + directory;
        }
        File file = new File(RuntimeEnvironment.getInstance().getSourceRootFile(), directory);
        if (file.exists()) {
            directories.add(directory);
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Update the content of this index database
     * @throws IOException if an error occurs
     * @throws HistoryException if an error occurs when accessing the history
     */
    public void update() throws IOException, HistoryException {
        synchronized (lock) {
            if (running) {
                throw new IOException("Indexer already running!");
            }
            running = true;
            interrupted = false;
        }

        String ctgs = RuntimeEnvironment.getInstance().getCtags();
        if (ctgs != null) {
            ctags = new Ctags();
            ctags.setBinary(ctgs);
        }
        if (ctags == null) {
            log.severe("Unable to run ctags! searching definitions will not work!");
        }

        try {
            //TODO we might need to add writer.commit after certain phases of index generation, right now it will only happen in the end
            writer = new IndexWriter(indexDirectory, AnalyzerGuru.getAnalyzer(),IndexWriter.MaxFieldLength.UNLIMITED);
            writer.setMaxFieldLength(RuntimeEnvironment.getInstance().getIndexWordLimit());

            if (directories.isEmpty()) {
                if (project == null) {
                    directories.add("");
                } else {
                    directories.add(project.getPath());
                }
            }

            for (String dir : directories) {
                File sourceRoot;
                if ("".equals(dir)) {
                    sourceRoot = RuntimeEnvironment.getInstance().getSourceRootFile();
                } else {
                    sourceRoot = new File(RuntimeEnvironment.getInstance().getSourceRootFile(), dir);
                }
                
                HistoryGuru.getInstance().ensureHistoryCacheExists(sourceRoot);

                String startuid = Util.uid(dir, "");
                IndexReader reader = IndexReader.open(indexDirectory,false); // open existing index
                try {
                    uidIter = reader.terms(new Term("u", startuid)); // init uid iterator

                    //TODO below should be optional, since it traverses the tree once more to get total count! :(
                    int file_cnt = 0;
                    log.log(Level.INFO, "Counting files in {0} ...", dir);
                    file_cnt = indexDown(sourceRoot, dir, true, 0, 0);
                    if (log.isLoggable(Level.INFO)) {
                    log.log(Level.INFO, "Need to process: {0} files for {1}", new Object[]{file_cnt,dir});
                    }

                    indexDown(sourceRoot, dir, false, 0, file_cnt);

                    while (uidIter.term() != null && uidIter.term().field().equals("u") && uidIter.term().text().startsWith(startuid)) {
                        removeFile();
                        uidIter.next();
                    }
                } finally {
                    reader.close();
                }
            }
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    log.log(Level.WARNING, "An error occured while closing writer", e);                    
                }
            }

            if (ctags != null) {
                try {
                    ctags.close();
                } catch (IOException e) {
                    log.log(Level.WARNING, "An error occured while closing ctags process", e);
                }
            }

            synchronized (lock) {
                running = false;
            }
        }

        if (!isInterrupted() && isDirty()) {
            if (RuntimeEnvironment.getInstance().isOptimizeDatabase()) {
                optimize();
            }
            createSpellingSuggestions();
            RuntimeEnvironment env = RuntimeEnvironment.getInstance();
            File timestamp = new File(env.getDataRootFile(), "timestamp");
            if (timestamp.exists()) {
                if (!timestamp.setLastModified(System.currentTimeMillis())) {
                   log.log(Level.WARNING, "Failed to set last modified time on ''{0}'', used for timestamping the index database.", timestamp.getAbsolutePath());
                }
            } else {
                if (!timestamp.createNewFile()) {
                   log.log(Level.WARNING, "Failed to create file ''{0}'', used for timestamping the index database.", timestamp.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Optimize all index databases
     * @param executor An executor to run the job
     * @throws IOException if an error occurs
     */
    static void optimizeAll(ExecutorService executor) throws IOException {
        List<IndexDatabase> dbs = new ArrayList<IndexDatabase>();        
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.hasProjects()) {
            for (Project project : env.getProjects()) {
                dbs.add(new IndexDatabase(project));
            }
        } else {
            dbs.add(new IndexDatabase());
        }

        for (IndexDatabase d : dbs) {
            final IndexDatabase db = d;
            if (db.isDirty()) {
                executor.submit(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            db.update();
                        } catch (Exception e) {
                            log.log(Level.FINE,"Problem updating lucene index database: ",e);
                        }
                    }
                });
            }
        }
    }
    
    /**
     * Optimize the index database
     */
    public void optimize() {
        synchronized (lock) {
            if (running) {
                log.warning("Optimize terminated... Someone else is updating / optimizing it!");
                return ;
            }
            running = true;
        }
        IndexWriter wrt = null;
        try {            
            log.info("Optimizing the index ... ");            
            wrt = new IndexWriter(indexDirectory, null, false,IndexWriter.MaxFieldLength.UNLIMITED);
            wrt.optimize();            
            log.info("done");            
            synchronized (lock) {
                if (dirtyFile.exists() && !dirtyFile.delete()) {
                    log.log(Level.FINE, "Failed to remove \"dirty-file\": {0}", dirtyFile.getAbsolutePath());
                }
                dirty = false;
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "ERROR: optimizing index: {0}", e);
        } finally {
            if (wrt != null) {
                try {
                    wrt.close();
                } catch (IOException e) {
                    log.log(Level.WARNING, "An error occured while closing writer", e);
                }
            }
            synchronized (lock) {
                running = false;
            }
        }
    }

    /**
     * Generate a spelling suggestion for the definitions stored in defs
     */
    public void createSpellingSuggestions() {
        IndexReader indexReader = null;
        SpellChecker checker = null;

        try {            
            log.info("Generating spelling suggestion index ... ");            
            indexReader = IndexReader.open(indexDirectory,false);
            checker = new SpellChecker(spellDirectory);
            //TODO below seems only to index "defs" , possible bug ?
            checker.indexDictionary(new LuceneDictionary(indexReader, "defs"));            
            log.info("done");            
        } catch (IOException e) {
            log.log(Level.SEVERE, "ERROR: Generating spelling: {0}", e);
        } finally {
            if (indexReader != null) {
                try {
                    indexReader.close();
                } catch (IOException e) {
                    log.log(Level.WARNING, "An error occured while closing reader", e);
                }
            }
            if (spellDirectory != null) {
                spellDirectory.close();
            }
        }
    }

    private boolean isDirty() {
        synchronized (lock) {
            return dirty;
        }
    }

    private void setDirty() {
        synchronized (lock) {
            try {
                if (!dirty && !dirtyFile.createNewFile()) {
                    if (!dirtyFile.exists()) {
                       log.log(Level.FINE,
                               "Failed to create \"dirty-file\": {0}",
                               dirtyFile.getAbsolutePath());
                    }
                    dirty = true;
                }
            } catch (IOException e) {
                log.log(Level.FINE,"When creating dirty file: ",e);
            }
        }
    }
    /**
     * Remove a stale file (uidIter.term().text()) from the index database 
     * (and the xref file)
     * @throws java.io.IOException if an error occurs
     */
    private void removeFile() throws IOException {
        String path = Util.uid2url(uidIter.term().text());

        for (IndexChangedListener listener : listeners) {
            listener.fileRemove(path);
        }
        writer.deleteDocuments(uidIter.term());

        File xrefFile;
        if (RuntimeEnvironment.getInstance().isCompressXref()) {
            xrefFile = new File(xrefDir, path + ".gz");
        } else {
            xrefFile = new File(xrefDir, path);
        }
        File parent = xrefFile.getParentFile();

        if (!xrefFile.delete() && xrefFile.exists()) {
            log.log(Level.INFO, "Failed to remove obsolete xref-file: {0}", xrefFile.getAbsolutePath());
        }

        // Remove the parent directory if it's empty
        if (parent.delete()) {
            log.log(Level.FINE, "Removed empty xref dir:{0}", parent.getAbsolutePath());
        }
        setDirty();
        for (IndexChangedListener listener : listeners) {
            listener.fileRemoved(path);
        }        
    }

    /**
     * Add a file to the Lucene index (and generate a xref file)
     * @param file The file to add
     * @param path The path to the file (from source root)
     * @throws java.io.IOException if an error occurs
     */
    private void addFile(File file, String path) throws IOException {
        final InputStream in =
                new BufferedInputStream(new FileInputStream(file));
        try {
            FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, path);            
            for (IndexChangedListener listener : listeners) {
                listener.fileAdd(path, fa.getClass().getSimpleName());
            }
            fa.setCtags(ctags);
            fa.setProject(Project.getProject(path));

            Document d;
            try {
                d = analyzerGuru.getDocument(file, in, path, fa);
            } catch (Exception e) {
                log.log(Level.INFO,
                        "Skipped file ''{0}'' because the analyzer didn''t " +
                        "understand it.",
                        path);
                log.log(Level.FINE, "Exception from analyzer:", e);
                return;
            }

            writer.addDocument(d, fa);
            Genre g = fa.getFactory().getGenre();
            if (xrefDir != null && (g == Genre.PLAIN || g == Genre.XREFABLE)) {
                File xrefFile = new File(xrefDir, path);
                // If mkdirs() returns false, the failure is most likely
                // because the file already exists. But to check for the
                // file first and only add it if it doesn't exists would
                // only increase the file IO...
                if (!xrefFile.getParentFile().mkdirs()) {
                    assert xrefFile.getParentFile().exists();
                }
                fa.writeXref(xrefDir, path);
            }
            setDirty();
            for (IndexChangedListener listener : listeners) {
                listener.fileAdded(path, fa.getClass().getSimpleName());
            }
        } finally {
            in.close();
        }
    }

    /**
     * Check if I should accept this file into the index database
     * @param file the file to check
     * @return true if the file should be included, false otherwise
     */
    private boolean accept(File file) {

        if (!includedNames.isEmpty() &&
           // the filter should not affect directory names
            (!(file.isDirectory() || includedNames.match(file))) ) {
                return false;
        }
        if (ignoredNames.ignore(file)) {
            return false;
        }

        String absolutePath = file.getAbsolutePath();

        if (!file.canRead()) {
            log.log(Level.WARNING, "Warning: could not read {0}", absolutePath);
            return false;
        }

        try {
            String canonicalPath = file.getCanonicalPath();
            if (!absolutePath.equals(canonicalPath) && !acceptSymlink(absolutePath, canonicalPath)) {
                log.log(Level.FINE, "Skipped symlink ''{0}'' -> ''{1}''", new Object[]{absolutePath, canonicalPath});
                return false;
            }
            //below will only let go files and directories, anything else is considered special and is not added
            if (!file.isFile() && !file.isDirectory()) {
                log.log(Level.WARNING, "Warning: ignored special file {0}", absolutePath);
                    return false;
            }
        } catch (IOException exp) {
            log.log(Level.WARNING, "Warning: Failed to resolve name: {0}", absolutePath);
            log.log(Level.FINE,"Stack Trace: ",exp);       
        }

        if (file.isDirectory()) {
            // always accept directories so that their files can be examined
            return true;
        }

        if (HistoryGuru.getInstance().hasHistory(file)) {
            // versioned files should always be accepted
            return true;
        }

        // this is an unversioned file, check if it should be indexed
        return !RuntimeEnvironment.getInstance().isIndexVersionedFilesOnly();
    }

    /**
     * Check if I should accept the path containing a symlink
     * @param absolutePath the path with a symlink to check
     * @param canonicalPath the canonical path to the file
     * @return true if the file should be accepted, false otherwise
     */
    private boolean acceptSymlink(String absolutePath, String canonicalPath) throws IOException {
        // Always accept local symlinks
        if (isLocal(canonicalPath)) {
            return true;
        }

        for (String allowedSymlink : RuntimeEnvironment.getInstance().getAllowedSymlinks()) {
            if (absolutePath.startsWith(allowedSymlink)) {
                String allowedTarget = new File(allowedSymlink).getCanonicalPath();
                if (canonicalPath.startsWith(allowedTarget) &&
                    absolutePath.substring(allowedSymlink.length()).equals(canonicalPath.substring(allowedTarget.length()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if a file is local to the current project. If we don't have
     * projects, check if the file is in the source root.
     *
     * @param path the path to a file
     * @return true if the file is local to the current repository
     */
    private boolean isLocal(String path) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String srcRoot = env.getSourceRootPath();

        boolean local = false;

        if (path.startsWith(srcRoot)) {
            if (env.hasProjects()) {
                String relPath = path.substring(srcRoot.length());
                if (project.equals(Project.getProject(relPath))) {
                    // File is under the current project, so it's local.
                    local = true;
                }
            } else {
                // File is under source root, and we don't have projects, so
                // consider it local.
                local = true;
            }
        }

        return local;
    }

    /**
     * Generate indexes recursively
     * @param dir the root indexDirectory to generate indexes for
     * @param path the path
     * @param count_only if true will just traverse the source root and count files
     * @param cur_count current count during the traversal of the tree
     * @param est_total estimate total files to process
     *
     */
    private int indexDown(File dir, String parent, boolean count_only, int cur_count, int est_total) throws IOException {
        int lcur_count=cur_count;
        if (isInterrupted()) {
            return lcur_count;
        }

        if (!accept(dir)) {
            return lcur_count;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            log.log(Level.SEVERE, "Failed to get file listing for: {0}", dir.getAbsolutePath());
            return lcur_count;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
                public int compare(File p1, File p2) {
                    return p1.getName().compareTo(p2.getName());
                }
            });

        for (File file : files) {
            if (accept(file)) {
                String path = parent + '/' + file.getName();

                if (file.isDirectory()) {
                    lcur_count = indexDown(file, path, count_only, lcur_count, est_total);
                } else {
                    lcur_count++;
                    if (count_only) {
                        continue;
                    }

                    if (est_total > 0 && log.isLoggable(Level.INFO) )
                    {                        
                        log.log(Level.INFO, "Progress: {0} ({1}%)", new Object[]{lcur_count, (lcur_count * 100.0f / est_total) });                        
                    }

                    if (uidIter != null) {
                        String uid = Util.uid(path, DateTools.timeToString(file.lastModified(), DateTools.Resolution.MILLISECOND)); // construct uid for doc
                        while (uidIter.term() != null && uidIter.term().field().equals("u") &&
                                uidIter.term().text().compareTo(uid) < 0) {
                            removeFile();
                            uidIter.next();
                        }

                        if (uidIter.term() != null && uidIter.term().field().equals("u") &&
                                uidIter.term().text().compareTo(uid) == 0) {
                            uidIter.next(); // keep matching docs
                            continue;
                        }
                    }
                    try {
                        addFile(file, path);
                    } catch (Exception e) {
                        log.log(Level.WARNING,
                                "Failed to add file " + file.getAbsolutePath(),
                                e);
                    }
                }
            }
        }

        return lcur_count;
    }

    /**
     * Interrupt the index generation (and the index generation will stop as
     * soon as possible)
     */
    public void interrupt() {
        synchronized (lock) {
            interrupted = true;
        }
    }

    private boolean isInterrupted() {
        synchronized (lock) {
            return interrupted;
        }        
    }

    /**
     * Register an object to receive events when modifications is done to the
     * index database.
     * 
     * @param listener the object to receive the events
     */
    public void addIndexChangedListener(IndexChangedListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove an object from the lists of objects to receive events when
     * modifications is done to the index database
     * 
     * @param listener the object to remove
     */
    public void removeIndexChangedListener(IndexChangedListener listener) {
        listeners.remove(listener);
    }

    /**
     * List all files in all of the index databases
     * @throws IOException if an error occurs
     */
    public static void listAllFiles() throws IOException {
        listAllFiles(null);
    }

    /**
     * List all files in some of the index databases
     * @param subFiles Subdirectories for the various projects to list the files
     *                 for (or null or an empty list to dump all projects)
     * @throws IOException if an error occurs
     */
    public static void listAllFiles(List<String> subFiles) throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.hasProjects()) {
            if (subFiles == null || subFiles.isEmpty()) {
                for (Project project : env.getProjects()) {
                    IndexDatabase db = new IndexDatabase(project);
                    db.listFiles();
                }
            } else {
                for (String path : subFiles) {
                    Project project = Project.getProject(path);
                    if (project == null) {
                        log.log(Level.WARNING, "Warning: Could not find a project for \"{0}\"", path);
                    } else {
                        IndexDatabase db = new IndexDatabase(project);
                        db.listFiles();
                    }
                }
            }
        } else {
            IndexDatabase db = new IndexDatabase();
            db.listFiles();
        }
    }

    /**
     * List all of the files in this index database
     * 
     * @throws IOException If an IO error occurs while reading from the database
     */
    public void listFiles() throws IOException {
        IndexReader ireader = null;
        TermEnum iter = null;

        try {
            ireader = IndexReader.open(indexDirectory,false); // open existing index
            iter = ireader.terms(new Term("u", "")); // init uid iterator
            while (iter.term() != null) {
                log.fine(Util.uid2url(iter.term().text()));
                iter.next();
            }
        } finally {
            if (iter != null) {
                try {
                    iter.close();
                } catch (IOException e) {
                    log.log(Level.WARNING, "An error occured while closing index iterator", e);
                }
            }

            if (ireader != null) {
                try {
                    ireader.close();
                } catch (IOException e) {
                    log.log(Level.WARNING, "An error occured while closing index reader", e);
                }
            }
        }
    }

    static void listFrequentTokens() throws IOException {
        listFrequentTokens(null);
    }

    static void listFrequentTokens(List<String> subFiles) throws IOException {
        final int limit = 4;

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.hasProjects()) {
            if (subFiles == null || subFiles.isEmpty()) {
                for (Project project : env.getProjects()) {
                    IndexDatabase db = new IndexDatabase(project);
                    db.listTokens(4);
                }
            } else {
                for (String path : subFiles) {
                    Project project = Project.getProject(path);
                    if (project == null) {
                        log.log(Level.WARNING, "Warning: Could not find a project for \"{0}\"", path);
                    } else {
                        IndexDatabase db = new IndexDatabase(project);
                        db.listTokens(4);
                    }
                }
            }
        } else {
            IndexDatabase db = new IndexDatabase();
            db.listTokens(limit);
        }
    }

    public void listTokens(int freq) throws IOException {
        IndexReader ireader = null;
        TermEnum iter = null;

        try {
            ireader = IndexReader.open(indexDirectory,false);
            iter = ireader.terms(new Term("defs", ""));
            while (iter.term() != null) {
                if (iter.term().field().startsWith("f")) {
                    if (iter.docFreq() > 16 && iter.term().text().length() > freq) {
                        log.warning(iter.term().text());
                    }
                    iter.next();
                } else {
                    break;
                }
            }            
        } finally {
            if (iter != null) {
                try {
                    iter.close();
                } catch (IOException e) {
                    log.log(Level.WARNING, "An error occured while closing index iterator", e);
                }
            }

            if (ireader != null) {
                try {
                    ireader.close();
                } catch (IOException e) {
                    log.log(Level.WARNING, "An error occured while closing index reader", e);
                }
            }
        }
    }
    
    /**
     * Get an indexReader for the Index database where a given file
     * @param path the file to get the database for
     * @return The index database where the file should be located or null if
     *         it cannot be located.
     */
    public static IndexReader getIndexReader(String path) {
        IndexReader ret = null;

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        File indexDir = new File(env.getDataRootFile(), "index");

        if (env.hasProjects()) {
            Project p = Project.getProject(path);
            if (p == null) {
                return null;
            } else {
                indexDir = new File(indexDir, p.getPath());
            }
        }
            try {
                FSDirectory fdir=FSDirectory.open(indexDir,NoLockFactory.getNoLockFactory());
                if (indexDir.exists() && IndexReader.indexExists(fdir)) {
                    ret = IndexReader.open(fdir,false);
                }
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Failed to open index: {0}", indexDir.getAbsolutePath());
                log.log(Level.FINE,"Stack Trace: ",ex);
            }
        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final IndexDatabase other = (IndexDatabase) obj;
        if (this.project != other.project && (this.project == null || !this.project.equals(other.project))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + (this.project == null ? 0 : this.project.hashCode());
        return hash;
    }
    
}
