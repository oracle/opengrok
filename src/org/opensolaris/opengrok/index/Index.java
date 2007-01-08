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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"%Z%%M% %I%     %E% SMI"
 */

package org.opensolaris.opengrok.index;
import java.io.*;
import java.util.*;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.spell.NGramSpeller;
import org.opensolaris.opengrok.analysis.*;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.web.Util;

/**
 * Creates and updates an inverted source index
 * as well as generates Xref, file stats etc., if specified
 * in the options
 */

class Index {
    private File indexDir;
    private boolean deleting;	// true during deletion pass
    private IndexReader reader;		// existing index
    private IndexWriter writer;		// new index being built
    private TermEnum uidIter;		// document id iterator
    private boolean create = true;
    private File xrefDir = null;
    private boolean changed;
    private AnalyzerGuru af;
    private Printer err;
    private Printer out;
    
    public Index(Printer out, Printer err) {
        this.err = err;
        this.out = out;
        try {
        } catch ( Exception e) {
            System.err.println("Error: [ main ] " + e);
            String msg = e.getMessage();
            if(msg != null && msg.startsWith("Lock obtain")) {
                System.err.println("Solution: If no other process is using the index, please remove the above lock file and run this command again");
            } else {
                //          try {
                //                  if (reader != null && dataRoot != null && reader.isLocked(dataRoot + "/index")) {
                //                      reader.unlock(FSDirectory.getDirectory(dataRoot + "/index", false) );
                //      }
//            } catch (IOException eio) {
                //              if (verbose) System.err.println("Warning: Could not delete lock file!");
                //        }
            }
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void cancel() {
        if(uidIter != null) {
            try {
                uidIter.close();
            } catch (IOException ex) {
            }
        }
        if(reader != null) {
            try {
                reader.close();
            } catch (IOException ex) {
            }
        }
        if(writer != null) {
            try {
                writer.close();
            } catch (IOException ex) {
            }
        }
        try {
            if(indexDir != null && IndexReader.isLocked(indexDir.getPath())) {
                IndexReader.unlock(FSDirectory.getDirectory(indexDir, false));
            }
        } catch (IOException e) {}
    }
    /*
     * Runs the indexing from arguments
     * @param dataRoot directory where search Index and all other data are stored
     * @param srcRoot root of the source tree
     * @param subFiles children which need to be updated/indexed. Pass null to index all of srcRoot
     * @param economical Should Xref HTML files be generated?
     */
    public int runIndexer(File dataRoot,
            File srcRootDir,
            ArrayList<String> subFiles,
            boolean economical) throws IOException {
        try {
            if(!dataRoot.exists()) {
                dataRoot.mkdirs();
            }
            
            String srcRootPath = srcRootDir.getAbsolutePath();
            File srcConfigFile = new File(dataRoot, "SRC_ROOT");
            try {
                FileWriter srcConfig = new FileWriter(srcConfigFile);
                srcConfig.write(srcRootPath+"\n");
                srcConfig.close();
            } catch(IOException e) {
                err.println("WARNING: Could not save source root name in " + dataRoot.getPath() + "/SRC_ROOT");
            }
            
            indexDir = new File(dataRoot, "index");
            
            if (!economical) {
                xrefDir = new File(dataRoot, "xref");
                if(!xrefDir.exists()) {
                    xrefDir.mkdirs();
                }
            }
            
            if(indexDir.isDirectory() && (new File(indexDir, "segments")).exists()) {
                create = false;
            }
            
            if(subFiles == null) {
                subFiles = new ArrayList<String>();
            }
            if(subFiles.size() == 0) {
                String[] allSubFiles = srcRootDir.list();
                if (allSubFiles != null) {
                    for(String sub: allSubFiles) {
                        if(!IgnoredNames.ignore(sub)) subFiles.add(sub);
                    }
                }
            }
            HashMap<File, String> inputSources = new HashMap<File,String>();
            for(String sub: subFiles) {
                File subFile = new File(srcRootDir, sub);
                if (!subFile.exists()) {
                    subFile = new File(sub);
                }
                if(subFile.canRead()) {
                    String subFilePath = subFile.getAbsolutePath();
                    if (subFilePath.startsWith(srcRootPath)) {
                        int subNameLength = subFile.getName().length();
                        int srcRootLength = srcRootPath.length();
                        if (subFilePath.length() <= srcRootLength) {
                            err.println("WARNING: " + sub + " is not under " + srcRootDir.getName());
                            continue;
                        }
                        String parent;
                        if((srcRootLength + subNameLength + 1) == subFilePath.length()) {
                            parent = "";
                        } else {
                            parent = subFilePath.substring(srcRootLength, subFilePath.length() - subNameLength-1);
                        }
                        inputSources.put(subFile, parent);
                        if(!subFile.isDirectory() && !economical && parent.length() > 0) {
                            (new File(xrefDir, parent)).mkdirs();
                        }
                    } else {
                        System.err.println("WARNING: " + sub + " is not under " + srcRootDir.getName());
                    }
                } else {
                    System.err.println("WARNING: Can not read " + sub);
                }
            }
            
            boolean anythingChanged = create;
            if(inputSources.size() == 0) {
                err.println("WARNING: nothing to index!");
                return 0;
            }
            
            for(File src: inputSources.keySet()) {
                out.println("Processing " + src.getName());
                changed = false;
                if (!create) {
                    out.println("Checking for changes in " + src.getName());
                    deleting = true;
                    startIndexing(src, indexDir, inputSources.get(src));
                }
                
                if (changed) anythingChanged = true;
                if (create || changed) {
                    if(af == null)
                        af = new AnalyzerGuru();
                    try {
                        writer = new IndexWriter(indexDir, af.getAnalyzer(), create);
                    } catch (IOException e) {
                        String msg = e.getMessage();
                        if(msg != null && msg.startsWith("Lock obtain")) {
                            //forcefully unlock the index
                            try {
                                if (IndexReader.isLocked(dataRoot + "/index")) {
                                    IndexReader.unlock(FSDirectory.getDirectory(dataRoot + "/index", false) );
                                }
                            } catch (Exception ex) {
                            }
                        }
                    }
                    if(writer == null) {
                        writer = new IndexWriter(indexDir, af.getAnalyzer(), create);
                    }
                    writer.maxFieldLength = 60000;
                    /*writer.mergeFactor = 1000;
                    writer.maxMergeDocs = 100000;
                    writer.minMergeDocs = 1000;*/
                    try {
                        startIndexing(src, indexDir, inputSources.get(src));
                        writer.close();
                    } catch (IOException e) {
                        try {
                            if (reader != null && dataRoot != null && reader.isLocked(dataRoot + "/index")) {
                                reader.unlock(FSDirectory.getDirectory(dataRoot + "/index", false) );
                            }
                        } catch (IOException eio) {
                            out.println("Warning: Could not delete lock file!");
                        }
                        throw e;
                    }
                    create = false;
                }
            }
            if(!anythingChanged){
                out.println("Nothing changed since last run");
            } else  {
                out.print("Optimizing the index ... ");
                doOptimize(dataRoot);
                out.println("done");
                if(!economical) {
                    out.print("Generating spelling suggestion index ... ");
                    File spellIndex = new File(dataRoot, "spellIndex");
                    IndexReader reader = IndexReader.open(indexDir);
                    IndexWriter swriter = new IndexWriter(spellIndex, new WhitespaceAnalyzer(), true);
                    NGramSpeller.formNGramIndex(reader, swriter, 3, 4, "defs", 5);
                    swriter.optimize();
                    swriter.close();
                    reader.close();
                    out.println("done");
                }
            }
            return 1;
        } catch (RuntimeException e) {
            if (reader != null && dataRoot != null && reader.isLocked(dataRoot + "/index")) {
                reader.unlock(FSDirectory.getDirectory(dataRoot + "/index", false));
            }
            throw e;
        }
    }
    
    /*
     * It is basically diffing two sorted lists of file names
     * agumented with its last modified timestamp:
     * (1) the list of files in the index
     *     The uidIter gives a list of files in index sorted.
     * (2) the list of files on disk
     *     traversing the directory tree recursively gives list of files
     *     on disk
     *  Algorithm is simple:
     *     while(each list has elements) {
     *	 if (elem1 < elem2)
     *	    delete elem1
     *	    list1.next()
     *      else if elem1 == elem2
     *	    do nothing
     *      else
     *	    add elem2
     *	    list2.next()
     *     }
     *    delete all remaining elements of list1
     *    add all remaining elements of list2
     *
     * It makes a two pass over the file tree.
     * Entire ON traversal took 10-20 secs.
     * May need to optimize if this gets worse.
     */
    private void startIndexing(File file, File indexDir, String parent) throws IOException {
        if (!create) {
            String startuid =  Util.uid(parent + '/' + file.getName(), "");
            //System.out.println("Start uid = " + startuid);
            reader = IndexReader.open(indexDir);		 // open existing index
            uidIter = reader.terms(new Term("u", startuid)); // init uid iterator
            indexDown(file, parent);
            if (deleting) {		   // delete rest of stale docs
                while (uidIter.term() != null && uidIter.term().field().equals("u") && uidIter.term().text().startsWith(startuid)) {
                    out.println(" - " + Util.uid2url(uidIter.term().text()));
                    reader.delete(uidIter.term());
                    uidIter.next();
                }
                deleting = false;
            }
            uidIter.close();    // close uid iterator
            reader.close();     // close existing index
            uidIter = null;
        } else  //creating
            indexDown(file, parent);
    }
    
    private void indexDown(File file, String parent) throws IOException {
        if(!file.canRead()) {
            err.println("Warning: could not read " + file.getName());
            return;
        }
        if(!file.getAbsolutePath().equals(file.getCanonicalPath())) {
            err.println("Warning: ignored link " + file.getName());
            return;
        }
        //SizeandLines rets = new SizeandLines();
        if (file.isDirectory()) {
            if(!IgnoredNames.ignore(file)) { // if a directory
                String[] files = file.list();
                if (files != null && files.length > 0) {
                    //SizeandLines ret = new SizeandLines();
                    Arrays.sort(files);
                    String path = parent + '/' +file.getName();
                    if (xrefDir != null) {
                        (new File(xrefDir, path)).mkdirs();
                    }
                    for (int i = 0; i < files.length; i++) {
                        if (!IgnoredNames.ignore(files[i])) {
                            indexDown(new File(file, files[i]), path);
                        }
                    }
                }
            }
        } else {
            if(!IgnoredNames.glob.accept(file)) {
                err.println("Warning: ignored file " + file.getName());
                return;
            }
           
            String path = parent + '/' + file.getName();
            if (uidIter != null) {
                String uid = Util.uid(path, DateField.timeToString(file.lastModified()));	 // construct uid for doc
                while (uidIter.term() != null && uidIter.term().field().equals("u") &&
                        uidIter.term().text().compareTo(uid) < 0) {
                    if (deleting) {	   // delete stale docs
                        out.println(" - " + Util.uid2url(uidIter.term().text()));
                        reader.delete(uidIter.term());
                        changed = true;
                    }
                    uidIter.next();
                }
                if (uidIter.term() != null && uidIter.term().field().equals("u") &&
                        uidIter.term().text().compareTo(uid) == 0) {
                    uidIter.next();		   // keep matching docs
                } else {
                    if (!deleting) {		      // add new docs
                        InputStream in = new BufferedInputStream(new FileInputStream(file));
                        FileAnalyzer fa = af.getAnalyzer(in, path);
                        out.print(fa.getClass().getSimpleName());
                        Document d = af.getDocument(file, in, path);
                        if (d != null) {
                            out.println(" + " + path);
                            writer.addDocument(d, fa);
                            FileAnalyzer.Genre g = af.getGenre(fa.getClass());
                            if (xrefDir != null && (g == Genre.PLAIN || g == Genre.XREFABLE)) {
                                fa.writeXref(xrefDir, path);
                            }
                        } else {
                            err.println("Warning: did not add " + path);
                        }
                    } else {
                        changed = true;
                    }
                }
            } else {		      // creating a new index
                InputStream in = new BufferedInputStream(new FileInputStream(file));
                FileAnalyzer fa = af.getAnalyzer(in, path);
                out.print(fa.getClass().getSimpleName());
                out.print(" ");
                Document d = af.getDocument(file, in, path);
                if (d != null) {
                    out.println(path);
                    writer.addDocument(d, fa);
                    Genre g = af.getGenre(fa.getClass());
                    if (xrefDir != null && (g == Genre.PLAIN || g == Genre.XREFABLE)) {
                        fa.writeXref(xrefDir, path);
                    }
                } else {
                    err.println("Warning: did not add " + path);
                }
            }
        }
    }
    
  /*
   * Merges fragmented indexes
   */
    public static void doOptimize(File dataRoot) {
        File indexDir = new File(dataRoot, "index");
        if (indexDir.isDirectory()) {
            try{
                IndexWriter writer = new IndexWriter(indexDir, null, false);
                writer.optimize();
                writer.close();
            } catch (IOException e) {
                System.err.println("ERROR: optimizing index: " + e);
            }
        } else {
            System.err.println("ERROR: " + indexDir.getPath() + " not a directory");
        }
    }
    
    /**
     * Generate a sorted list of "word"s
     */
    public static void doDict(File dataRoot) {
        try {
            IndexReader reader = IndexReader.open(new File(dataRoot, "index"));	      // open existing index
            TermEnum uidIter = reader.terms(new Term("defs", "")); // init uid iterator
            while (uidIter.term() != null) {
                if (uidIter.term().field().startsWith("f")) {
                    if (uidIter.docFreq() > 16 && uidIter.term().text().length() > 4) {
                        System.out.println(uidIter.term().text());
                    }
                    uidIter.next();
                } else {
                    break;
                }
            }
            uidIter.close();
            reader.close();
        } catch (IOException e) {
            System.err.println("ERROR: While generating dictionary " + dataRoot + ": " + e.getLocalizedMessage());
        }
    }
    
    /**
     * List all file names indexd
     */
    public static void doList(File dataRoot) {
        try {
            IndexReader reader = IndexReader.open(new File(dataRoot, "index"));	      // open existing index
            TermEnum uidIter = reader.terms(new Term("u", "")); // init uid iterator
            while (uidIter.term() != null) {
                System.out.println(Util.uid2url(uidIter.term().text()));
                uidIter.next();
            }
            uidIter.close();
            reader.close();
        } catch (IOException e) {
            System.err.println("ERROR: While listing files in index " + dataRoot + ": " + e.getLocalizedMessage());
        }
    }
    
    public static boolean setExuberantCtags(String ctags) {
        if (ctags == null) {
            ctags = RuntimeEnvironment.getInstance().getCtags();
        }
        
        // If no Path to CTags was specifyed we guess that its reachable ...
        if (ctags == null)
            ctags = "ctags";
        
        //Check if exub ctags is available
        Process ctagsProcess = null;
        try {
            ctagsProcess = Runtime.getRuntime().exec(new String[] {ctags, "--version" });
        } catch (Exception e) {
        }
        try {
            BufferedReader cin = new BufferedReader(new InputStreamReader(ctagsProcess.getInputStream()));
            String ctagOut;
            if((ctagOut = cin.readLine()) != null && ctagOut.startsWith("Exuberant Ctags")) {
                System.setProperty("ctags", ctags);
            } else {
                System.err.println("Error: No Exuberant Ctags found in PATH!\n" +
                        "(tried running " + ctags + ")\n" +
                        "Please use option -c to specify path to a good Exuberant Ctags program");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error: executing " + ctags + "! " +e.getLocalizedMessage() +
                    "\nPlease use option -c to specify path to a good Exuberant Ctags program");
            return false;
        }
        return true;
    }
}
