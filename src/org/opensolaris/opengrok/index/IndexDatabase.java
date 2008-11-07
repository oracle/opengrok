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
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.analysis.Ctags;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.web.Util;

/**
 * This class is used to create / update the index databases. Currently we use
 * one index database per project. 
 * 
 * @author Trond Norbye
 */
public class IndexDatabase {

    private Project project;
    private FSDirectory indexDirectory;
    private FSDirectory spellDirectory;
    private IndexWriter writer;
    private TermEnum uidIter;
    private IgnoredNames ignoredNames;
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

    /**
     * Create a new instance of the Index Database. Use this constructor if
     * you don't use any projects
     * 
     * @throws java.io.IOException if an error occurs while creating directories
     */
    public IndexDatabase() throws IOException {
        initialize();
    }

    /**
     * Create a new instance of an Index Database for a given project
     * @param project the project to create the database for
     * @throws java.io.IOException if an errror occurs while creating directories
     */
    public IndexDatabase(Project project) throws IOException {
        this.project = project;
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
                FSDirectory.setDisableLocks(true);
            }
            indexDirectory = FSDirectory.getDirectory(indexDir);
            spellDirectory = FSDirectory.getDirectory(spellDir);
            ignoredNames = env.getIgnoredNames();
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
     */
    public void update() throws IOException {
        synchronized (lock) {
            if (running) {
                throw new IOException("Indexer already running!");
            }
            running = true;
            interrupted = false;
        }

        try {
            ctags = new Ctags();
        } catch (IOException e) {
            log.log(Level.WARNING, "An error occured while creating ctags", e);
        }
        if (ctags == null) {
            log.severe("Unable to run ctags! searching definitions will not work!");
        }

        try {
            writer = new IndexWriter(indexDirectory, AnalyzerGuru.getAnalyzer());
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
                IndexReader reader = IndexReader.open(indexDirectory);		 // open existing index
                try {
                    uidIter = reader.terms(new Term("u", startuid)); // init uid iterator

                    indexDown(sourceRoot, dir);

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
                timestamp.setLastModified(System.currentTimeMillis());
            } else {
                timestamp.createNewFile();
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

                    public void run() {
                        try {
                            db.update();
                        } catch (IOException e) {
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
            if (RuntimeEnvironment.getInstance().isVerbose()) {
                log.info("Optimizing the index ... ");
            }
            wrt = new IndexWriter(indexDirectory, null, false);
            wrt.optimize();
            if (RuntimeEnvironment.getInstance().isVerbose()) {
                log.info("done");
            }
            synchronized (lock) {
                if (dirtyFile.exists() && !dirtyFile.delete()) {
                    log.fine("Failed to remove \"dirty-file\": " +
                            dirtyFile.getAbsolutePath());
                }
                dirty = false;
            }
        } catch (IOException e) {
            log.severe("ERROR: optimizing index: " + e);
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
            if (RuntimeEnvironment.getInstance().isVerbose()) {
                log.info("Generating spelling suggestion index ... ");
            }
            indexReader = IndexReader.open(indexDirectory);
            checker = new SpellChecker(spellDirectory);
            checker.indexDictionary(new LuceneDictionary(indexReader, "defs"));
            if (RuntimeEnvironment.getInstance().isVerbose()) {
                log.info("done");
            }
        } catch (IOException e) {
            log.severe("ERROR: Generating spelling: " + e);
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
                       log.log(Level.FINE, "Failed to create \"dirty-file\": ", dirtyFile.getAbsolutePath());
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
            listener.fileRemoved(path);
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
            log.info("Failed to remove obsolete xref-file: " +
                    xrefFile.getAbsolutePath());
        }

        // Remove the parent directory if it's empty
        if (parent.delete()) {
            log.fine("Removed empty xref dir:" + parent.getAbsolutePath());
        }

        setDirty();
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
            fa.setCtags(ctags);

            Document d = analyzerGuru.getDocument(file, in, path, fa);
            if (d == null) {
                log.warning("Warning: did not add " + path);
            } else {
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
        if (ignoredNames.ignore(file)) {
            return false;
        }

        if (!file.canRead()) {
            log.warning("Warning: could not read " + file.getAbsolutePath());
            return false;
        }

        try {
            if (!file.getAbsolutePath().equals(file.getCanonicalPath())) {
                if (file.getParentFile().equals(file.getCanonicalFile().getParentFile())) {
                    // Lets support symlinks within the same directory, this
                    // should probably be extended to within the same repository
                    return true;
                } else {
                    log.warning("Warning: ignored non-local symlink " + file.getAbsolutePath() +
                            " -> " + file.getCanonicalPath());
                    return false;
                }
            }
        } catch (IOException exp) {
            log.warning("Warning: Failed to resolve name: " + file.getAbsolutePath());
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
     * Generate indexes recursively
     * @param dir the root indexDirectory to generate indexes for
     * @param path the path
     */
    private void indexDown(File dir, String parent) throws IOException {
        if (isInterrupted()) {
            return;
        }

        if (!accept(dir)) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            log.severe("Failed to get file listing for: " + dir.getAbsolutePath());
            return;
        }
        Arrays.sort(files, new Comparator<File>() {

                public int compare(File p1, File p2) {
                    return p1.getName().compareTo(p2.getName());
                }
            });

        for (File file : files) {
            if (accept(file)) {
                String path = parent + '/' + file.getName();
                if (file.isDirectory()) {
                    indexDown(file, path);
                } else {
                    if (uidIter != null) {
                        String uid = Util.uid(path, DateTools.timeToString(file.lastModified(), DateTools.Resolution.MILLISECOND));	 // construct uid for doc
                        while (uidIter.term() != null && uidIter.term().field().equals("u") &&
                                uidIter.term().text().compareTo(uid) < 0) {
                            removeFile();
                            uidIter.next();
                        }

                        if (uidIter.term() != null && uidIter.term().field().equals("u") &&
                                uidIter.term().text().compareTo(uid) == 0) {
                            uidIter.next();		   // keep matching docs
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
                        log.warning("Warning: Could not find a project for \"" + path + "\"");
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
            ireader = IndexReader.open(indexDirectory);	      // open existing index
            iter = ireader.terms(new Term("u", "")); // init uid iterator
            while (iter.term() != null) {
                log.info(Util.uid2url(iter.term().text()));
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
                        log.warning("Warning: Could not find a project for \"" + path + "\"");
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
            ireader = IndexReader.open(indexDirectory);
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

        if (indexDir.exists() && IndexReader.indexExists(indexDir)) {
            try {
                ret = IndexReader.open(indexDir);
            } catch (Exception ex) {
                log.severe("Failed to open index: " + indexDir.getAbsolutePath());
                log.log(Level.FINE,"Stack Trace: ",ex); 
            }
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
