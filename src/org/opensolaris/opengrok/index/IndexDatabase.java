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
import java.util.List;
import java.util.concurrent.ExecutorService;
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
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
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
    private IndexReader reader;
    private TermEnum uidIter;
    private IgnoredNames ignoredNames;
    private AnalyzerGuru analyzerGuru;
    private File xrefDir;
    private boolean interrupted;
    private List<IndexChangedListener> listeners;
    private File dirtyFile;
    private boolean dirty;

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
     * @throws java.lang.Exception if an error occurs
     */
    public static void updateAll(ExecutorService executor) throws Exception {
        updateAll(executor, null);
    }

    /**
     * Update the index database for all of the projects
     * @param executor An executor to run the job
     * @param listener where to signal the changes to the database
     * @throws java.lang.Exception if an error occurs
     */
    static void updateAll(ExecutorService executor, IndexChangedListener listener) throws Exception {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.hasProjects()) {
            for (Project project : env.getProjects()) {
                final IndexDatabase db = new IndexDatabase(project);
                if (listener != null) {
                    db.addIndexChangedListener(listener);
                }
                executor.submit(new Runnable() {

                    public void run() {
                        try {
                            db.update();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } else {
            final IndexDatabase db = new IndexDatabase();
            if (listener != null) {
                db.addIndexChangedListener(listener);
            }
            
            executor.submit(new Runnable() {

                public void run() {
                    try {
                        db.update();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

    }

    private void initialize() throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        File indexDir = new File(env.getDataRootFile(), "index");
        File spellDir = new File(env.getDataRootFile(), "spellIndex");
        if (project != null) {
            indexDir = new File(indexDir, project.getPath());
            spellDir = new File(spellDir, project.getPath());
        }

        if (!indexDir.exists() || !spellDir.exists()) {
            indexDir.mkdirs();
            spellDir.mkdirs();
            // to avoid race conditions, just recheck..
            if (!indexDir.exists()) {
                throw new FileNotFoundException("Failed to create root directory [" + indexDir.getAbsolutePath() + "]");
            }
            if (!spellDir.exists()) {
                throw new FileNotFoundException("Failed to create root directory [" + spellDir.getAbsolutePath() + "]");
            }
        }

        indexDirectory = FSDirectory.getDirectory(indexDir);
        spellDirectory = FSDirectory.getDirectory(spellDir);
        ignoredNames = env.getIgnoredNames();
        analyzerGuru = new AnalyzerGuru();
        if (RuntimeEnvironment.getInstance().isGenerateHtml()) {
            xrefDir = new File(env.getDataRootFile(), "xref");
        }
        listeners = new ArrayList<IndexChangedListener>();
        dirtyFile = new File(indexDir, "dirty");
        dirty = dirtyFile.exists();
    }

    /**
     * Update the content of this index database
     * @throws java.lang.Exception if an error occurs
     */
    public synchronized void update() throws Exception {
        interrupted = false;
        try {
            writer = new IndexWriter(indexDirectory, AnalyzerGuru.getAnalyzer());
            writer.setMaxFieldLength(RuntimeEnvironment.getInstance().getIndexWordLimit());
            String root;
            File sourceRoot;

            if (project != null) {
                root = project.getPath();
                sourceRoot = new File(RuntimeEnvironment.getInstance().getSourceRootFile(), project.getPath());
            } else {
                root = "";
                sourceRoot = RuntimeEnvironment.getInstance().getSourceRootFile();
            }

            String startuid = Util.uid(root, "");
            reader = IndexReader.open(indexDirectory);		 // open existing index
            uidIter = reader.terms(new Term("u", startuid)); // init uid iterator

            indexDown(sourceRoot, root);

            while (uidIter.term() != null && uidIter.term().field().equals("u") && uidIter.term().text().startsWith(startuid)) {
                removeFile();
                uidIter.next();
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }

        if (!interrupted && dirty) {
            if (RuntimeEnvironment.getInstance().isOptimizeDatabase()) {
                optimize();
            }
            createSpellingSuggestions();
        }
    }

    /**
     * Optimize the index database
     */
    public void optimize() {
        IndexWriter wrt = null;
        try {
            if (RuntimeEnvironment.getInstance().isVerbose()) {
                System.out.print("Optimizing the index ... ");
            }
            wrt = new IndexWriter(indexDirectory, null, false);
            wrt.optimize();
            if (RuntimeEnvironment.getInstance().isVerbose()) {
                System.out.println("done");
            }
            dirtyFile.delete();
            dirty = false;
        } catch (IOException e) {
            System.err.println("ERROR: optimizing index: " + e);
        } finally {
            if (wrt != null) {
                try {
                    wrt.close();
                } catch (IOException e) {
                }
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
                System.out.print("Generating spelling suggestion index ... ");
            }
            indexReader = IndexReader.open(indexDirectory);
            checker = new SpellChecker(spellDirectory);
            checker.indexDictionary(new LuceneDictionary(indexReader, "defs"));
            if (RuntimeEnvironment.getInstance().isVerbose()) {
                System.out.println("done");
            }
        } catch (IOException e) {
            System.err.println("ERROR: Generating spelling: " + e);
        } finally {
            if (indexReader != null) {
                try {
                    indexReader.close();
                } catch (IOException e) {
                }
            }
            if (spellDirectory != null) {
                spellDirectory.close();
            }
        }
    }

    private void setDirty() {
        try {
            if (!dirty) {
                dirtyFile.createNewFile();
                dirty = true;
            }
        } catch (Exception e) { 
            e.printStackTrace();
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

        File xrefFile = new File(xrefDir, path);
        xrefFile.delete();
        xrefFile.getParentFile().delete();
        setDirty();
    }

    /**
     * Add a file to the Lucene index (and generate a xref file)
     * @param file The file to add
     * @param path The path to the file (from source root)
     * @throws java.io.IOException if an error occurs
     */
    private void addFile(File file, String path) throws IOException {
        InputStream in;
        try {
            in = new BufferedInputStream(new FileInputStream(file));
        } catch (IOException ex) {
            System.err.println("Warning: " + ex.getMessage());
            return;
        }
        FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, path);

        for (IndexChangedListener listener : listeners) {
            listener.fileAdded(path, fa.getClass().getSimpleName());
        }

        Document d = analyzerGuru.getDocument(file, in, path, fa);
        if (d != null) {
            writer.addDocument(d, fa);
            Genre g = fa.getFactory().getGenre();
            if (xrefDir != null && (g == Genre.PLAIN || g == Genre.XREFABLE)) {
                File xrefFile = new File(xrefDir, path);
                xrefFile.getParentFile().mkdirs();
                fa.writeXref(xrefDir, path);
            }
            setDirty();
        } else {
            System.err.println("Warning: did not add " + path);
        }

        try { in.close(); } catch (Exception e) {}
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
            System.err.println("Warning: could not read " + file.getAbsolutePath());
            return false;
        }

        try {
            if (!file.getAbsolutePath().equals(file.getCanonicalPath())) {
                System.err.println("Warning: ignored link " + file.getAbsolutePath() +
                        " -> " + file.getCanonicalPath());
                return false;
            }
        } catch (IOException exp) {
            System.err.println("Warning: Failed to resolve name: " + file.getAbsolutePath());
            exp.printStackTrace();
        }

        return true;
    }

    /**
     * Generate indexes recursively
     * @param dir the root indexDirectory to generate indexes for
     * @param path the path
     */
    private void indexDown(File dir, String parent) throws IOException {
        if (interrupted) {
            return;
        }

        if (!accept(dir)) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            System.err.println("Failed to get file listing for: " + dir.getAbsolutePath());
            return;
        }
        Arrays.sort(files);

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
                        } else {
                            addFile(file, path);
                        }
                    } else {
                        addFile(file, path);
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
        interrupted = true;
    }

    /**
     * Register an object to receive events when modifications is done to the
     * index database.
     * 
     * @param listener the object to receive the events
     */
    void addIndexChangedListener(IndexChangedListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove an object from the lists of objects to receive events when
     * modifications is done to the index database
     * 
     * @param listener the object to remove
     */
    void removeIndexChangedListener(IndexChangedListener listener) {
        listeners.remove(listener);
    }

    /**
     * List all files in all of the index databases
     * @throws java.lang.Exception if an error occurs
     */
    public static void listAllFiles() throws Exception {
        listAllFiles(null);
    }

    /**
     * List all files in some of the index databases
     * @param subFiles Subdirectories for the various projects to list the files
     *                 for (or null or an empty list to dump all projects)
     * @throws java.lang.Exception if an error occurs
     */
    public static void listAllFiles(List<String> subFiles) throws Exception {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (!env.hasProjects()) {
            IndexDatabase db = new IndexDatabase();
            db.listFiles();
        } else {
            if (subFiles == null || subFiles.isEmpty()) {
                for (Project project : env.getProjects()) {
                    IndexDatabase db = new IndexDatabase(project);
                    db.listFiles();
                }
            } else {
                for (String path : subFiles) {
                    Project project = Project.getProject(path);
                    if (project == null) {
                        System.err.println("Warning: Could not find a project for \"" + path + "\"");
                    } else {
                        IndexDatabase db = new IndexDatabase(project);
                        db.listFiles();
                    }
                }
            }
        }
    }

    /**
     * List all of the files in this index database
     * 
     * @throws java.lang.Exception if an error occurs
     */
    public void listFiles() throws Exception {
        IndexReader ireader = null;
        TermEnum iter = null;

        try {
            ireader = IndexReader.open(indexDirectory);	      // open existing index
            iter = ireader.terms(new Term("u", "")); // init uid iterator
            while (iter.term() != null) {
                System.out.println(Util.uid2url(iter.term().text()));
                iter.next();
            }
        } finally {
            if (iter != null) {
                try {
                    iter.close();
                } catch (Exception e) {
                }
            }

            if (ireader != null) {
                try {
                    ireader.close();
                } catch (Exception e) {
                }
            }
        }
    }

    static void listFrequentTokens() throws Exception {
        listFrequentTokens(null);
    }

    static void listFrequentTokens(ArrayList<String> subFiles) throws Exception {
        final int limit = 4;

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (!env.hasProjects()) {
            IndexDatabase db = new IndexDatabase();
            db.listTokens(limit);
        } else {
            if (subFiles == null || subFiles.isEmpty()) {
                for (Project project : env.getProjects()) {
                    IndexDatabase db = new IndexDatabase(project);
                    db.listTokens(4);
                }
            } else {
                for (String path : subFiles) {
                    Project project = Project.getProject(path);
                    if (project == null) {
                        System.err.println("Warning: Could not find a project for \"" + path + "\"");
                    } else {
                        IndexDatabase db = new IndexDatabase(project);
                        db.listTokens(4);
                    }
                }
            }
        }
    }

    public void listTokens(int freq) throws Exception {
        IndexReader ireader = null;
        TermEnum iter = null;

        try {
            ireader = IndexReader.open(indexDirectory);
            iter = ireader.terms(new Term("defs", ""));
            while (iter.term() != null) {
                if (iter.term().field().startsWith("f")) {
                    if (iter.docFreq() > 16 && iter.term().text().length() > freq) {
                        System.out.println(iter.term().text());
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
                } catch (Exception e) {
                }
            }

            if (ireader != null) {
                try {
                    ireader.close();
                } catch (Exception e) {
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
            if (p != null) {
                indexDir = new File(indexDir, p.getPath());
            } else {
                return null;
            }
        }

        if (indexDir.exists() && IndexReader.indexExists(indexDir)) {
            try {
                ret = IndexReader.open(indexDir);
            } catch (Exception ex) {
                System.err.println("Failed to open index: " + indexDir.getAbsolutePath());
                ex.printStackTrace();
            }
        }

        return ret;
    }
}
