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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.analysis.archive.BZip2Analyzer;
import org.opensolaris.opengrok.analysis.archive.GZIPAnalyzer;
import org.opensolaris.opengrok.analysis.archive.TarAnalyzer;
import org.opensolaris.opengrok.analysis.archive.ZipAnalyzer;
import org.opensolaris.opengrok.analysis.c.CAnalyzer;
import org.opensolaris.opengrok.analysis.data.IgnorantAnalyzer;
import org.opensolaris.opengrok.analysis.data.ImageAnalyzer;
import org.opensolaris.opengrok.analysis.document.TroffAnalyzer;
import org.opensolaris.opengrok.analysis.executables.ELFAnalyzer;
import org.opensolaris.opengrok.analysis.executables.JarAnalyzer;
import org.opensolaris.opengrok.analysis.executables.JavaClassAnalyzer;
import org.opensolaris.opengrok.analysis.java.JavaAnalyzer;
import org.opensolaris.opengrok.analysis.lisp.LispAnalyzer;
import org.opensolaris.opengrok.analysis.plain.PlainAnalyzer;
import org.opensolaris.opengrok.analysis.plain.XMLAnalyzer;
import org.opensolaris.opengrok.analysis.sh.ShAnalyzer;
import org.opensolaris.opengrok.analysis.sql.SQLAnalyzer;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.history.HistoryReader;
import org.opensolaris.opengrok.web.Util;

/**
 * Manages and porvides Analyzers as needed. Please see
 * <a href="http://www.opensolaris.org/os/project/opengrok/manual/internals/">
 * this</a> page for a great description of the purpose of the AnalyzerGuru.
 *
 * Created on September 22, 2005
 * @author Chandan
 */
public class AnalyzerGuru {

    private static HashMap<String, Class<? extends FileAnalyzer>> ext;
    private static SortedMap<String, Class<? extends FileAnalyzer>> magics;
    private static ArrayList<Method> matchers;
    /*
     * If you write your own analyzer please register it here
     */
    private static ArrayList<Class<? extends FileAnalyzer>> analyzers = new ArrayList<Class<? extends FileAnalyzer>>();
    static {
        analyzers.add(IgnorantAnalyzer.class);
        analyzers.add(BZip2Analyzer.class);
        analyzers.add(FileAnalyzer.class);
        analyzers.add(XMLAnalyzer.class);
        analyzers.add(TroffAnalyzer.class);
        analyzers.add(ELFAnalyzer.class);
        analyzers.add(JavaClassAnalyzer.class);
        analyzers.add(ImageAnalyzer.class);
        analyzers.add(JarAnalyzer.class);
        analyzers.add(ZipAnalyzer.class);
        analyzers.add(TarAnalyzer.class);
        analyzers.add(CAnalyzer.class);
        analyzers.add(ShAnalyzer.class);
        analyzers.add(PlainAnalyzer.class);
        analyzers.add(GZIPAnalyzer.class);
        analyzers.add(JavaAnalyzer.class);
        analyzers.add(LispAnalyzer.class);
        analyzers.add(SQLAnalyzer.class);
    }
    private static HashMap<Class<? extends FileAnalyzer>, FileAnalyzer> analyzerInstances = new HashMap<Class<? extends FileAnalyzer>, FileAnalyzer>();
    /**
     * Initializes an AnalyzerGuru
     */
    static {
        if (ext == null) {
            ext = new HashMap<String, Class<? extends FileAnalyzer>>();
        }
        if (magics == null) {
            magics = new TreeMap<String, Class<? extends FileAnalyzer>>();
            // TODO: have a comparator
        }
        if (matchers == null) {
            matchers = new ArrayList<Method>();
        }
        for (Class<? extends FileAnalyzer> analyzer : analyzers) {
            try {
                String[] suffixes = (String[]) analyzer.getField("suffixes").get(null);
                for (String suffix : suffixes) {
                    //System.err.println(analyzer.getSimpleName() + " = " + suffix);
                    Class old = ext.put(suffix, analyzer);
                    assert old == null :
                        "suffix '" + suffix + "' used in multiple analyzers";
                }
            } catch (Exception e) {
                //   System.err.println("AnalyzerFinder:" + analyzer.getSimpleName() + e);
            }
            try {
                String[] smagics = (String[]) analyzer.getField("magics").get(null);
                for (String magic : smagics) {
                    //System.err.println(analyzer.getSimpleName() + " = " + magic);
                    magics.put(magic, analyzer);
                }
            } catch (Exception e) {
                //  System.err.println("AnalyzerFinder: " + analyzer.getSimpleName() + e);
            }
            try {
                Method m = analyzer.getMethod("isMagic", byte[].class);
                if (m != null) {
                    matchers.add(m);
                }
            } catch (Exception e) {
            }
        }
        //System.err.println("Exts " + ext);
        //System.err.println("Matchers " + matchers);
    }

    /**
     *  Instruct the AnalyzerGuru to use a given analyzer for a given
     *  file extension.
     *  @param extension the file-extension to add
     *  @param analyzer the analyzer to use for the given extension
     *                  (if you pass null as the analyzer, you will disable
     *                   the analyzer used for that extension)
     */
    public static void addExtension(String extension, Class<? extends FileAnalyzer> analyzer) {
        ext.remove(extension);
        if (analyzer != null) {
            ext.put(extension, analyzer);
        }
    }

    /*
     * Get the default Analyzer.
     */
    public static FileAnalyzer getAnalyzer() {

        Class<FileAnalyzer> a = FileAnalyzer.class;
        FileAnalyzer fa = analyzerInstances.get(a);
        if (fa == null) {
            try {
                fa = a.newInstance();
                analyzerInstances.put(a, fa);
                return fa;
            } catch (Exception e) {
                System.err.println("ERROR: Initializing " + a);
            }
        }
        return fa;
    }

    /**
     * Get an analyzer suited to analyze a file. This function will reuse
     * analyzers since they are costly.
     * 
     * @param in Input stream containing data to be analyzed
     * @param file Name of the file to be analyzed
     * @return An analyzer suited for that file content
     * @throws java.io.IOException If an error occurs while accessing the
     *                             data in the input stream. 
     */
    public static FileAnalyzer getAnalyzer(InputStream in, String file) throws IOException {
        Class<? extends FileAnalyzer> a = find(in, file);
        if (a == null) {
            a = FileAnalyzer.class;
        }
        if (a != null) {
            FileAnalyzer fa = analyzerInstances.get(a);
            if (fa == null) {
                try {
                    fa = (FileAnalyzer) a.newInstance();
                    analyzerInstances.put(a, fa);
                    return fa;
                } catch (Exception e) {
                    System.err.println("ERROR: Initializing " + a);
                }
            } else {
                return fa;
            }
        }
        return null;
    }

    /**
     * Create a Lucene document and fill in the required fields
     * @param file The file to index
     * @param in The data to generate the index for
     * @param path Where the file is located (from source root)
     * @return The Lucene document to add to the index database
     * @throws java.io.IOException If an exception occurs while collecting the
     *                             datas
     */
    public Document getDocument(File file, InputStream in, String path) throws IOException {
        Document doc = new Document();
        String date = DateTools.timeToString(file.lastModified(), DateTools.Resolution.MILLISECOND);
        doc.add(new Field("u", Util.uid(path, date), Field.Store.YES, Field.Index.UN_TOKENIZED));
        doc.add(new Field("fullpath", file.getAbsolutePath(), Field.Store.YES, Field.Index.TOKENIZED));

        try {
            HistoryReader hr = HistoryGuru.getInstance().getHistoryReader(file);
            if (hr != null) {
                doc.add(new Field("hist", hr));
                // date = hr.getLastCommentDate() //RFE
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        doc.add(new Field("date", date, Field.Store.YES, Field.Index.UN_TOKENIZED));
        if (path != null) {
            doc.add(new Field("path", path, Field.Store.YES, Field.Index.TOKENIZED));
            Project project = Project.getProject(path);
            if (project != null) {
                doc.add(new Field("project", project.getPath(), Field.Store.YES, Field.Index.TOKENIZED));
            }
        }
        FileAnalyzer fa = null;
        try {
            fa = getAnalyzer(in, path);
        } catch (Exception e) {
        }
        if (fa != null) {
            try {
                Genre g = fa.getGenre();
                if (g == Genre.PLAIN) {
                    doc.add(new Field("t", "p", Field.Store.YES, Field.Index.UN_TOKENIZED));
                } else if (g == Genre.XREFABLE) {
                    doc.add(new Field("t", "x", Field.Store.YES, Field.Index.UN_TOKENIZED));
                } else if (g == Genre.HTML) {
                    doc.add(new Field("t", "h", Field.Store.YES, Field.Index.UN_TOKENIZED));
                }
                fa.analyze(doc, in);
            } catch (Exception e) {
                // Ignoring any errors while analysing
            }
        }
        doc.removeField("fullpath");

        return doc;
    }

    /**
     * Get the content type for a named file.
     *
     * @param file The file to get the content type for
     * @return The contentType suitable for printing to response.setContentType()
     */
    public static String getContentType(String file) {
        Class<? extends FileAnalyzer> a = find(file);
        return getContentType(a);
    }

    /**
     * Get the content type for a named file.
     *
     * @param in The input stream we want to get the content type for (if
     *           we cannot determine the content type by the filename)
     * @param file The name of the file
     * @return The contentType suitable for printing to response.setContentType()
     * @throws java.io.IOException If an error occurs while accessing the input
     *                             stream.
     */
    public static String getContentType(InputStream in, String file) throws IOException {
        Class<? extends FileAnalyzer> a = find(in, file);
        return getContentType(a);
    }

    /**
     * Get the content type the named analyzer accepts
     * @param analyzer the analyzer to test
     * @return the contentType suitable for printing to response.setContentType()
     */
    public static String getContentType(Class<? extends FileAnalyzer> analyzer) {
        String contentType = null;
        if (analyzer != null) {
            try {
                contentType = (String) analyzer.getMethod("getContentType").invoke(null);
            } catch (Exception e) {
            }
        }
        return contentType;
    }

    /**
     * Write a browsable version of the file
     *
     * @param analyzer The analyzer for this filetype
     * @param in The input stream containing the data
     * @param out Where to write the result
     * @param annotation Annotation information for the file
     * @throws java.io.IOException If an error occurs while creating the
     *                             output
     */
    public static void writeXref(Class<? extends FileAnalyzer> analyzer, InputStream in, Writer out, Annotation annotation) throws IOException {
        if (analyzer != null) {
            try {
                analyzer.getMethod("writeXref", InputStream.class, Writer.class, Annotation.class).invoke(null, in, out, annotation);
            } catch (IllegalArgumentException ex) {
            } catch (SecurityException ex) {
            } catch (NoSuchMethodException ex) {
            } catch (InvocationTargetException ex) {
            } catch (IllegalAccessException ex) {
            }
        }
    }

    /**
     * Get the genre of a file
     *
     * @param file The file to inpect
     * @return The genre suitable to decide how to display the file
     */
    public static Genre getGenre(String file) {
        Class a = find(file);
        return getGenre(a);
    }

    /**
     * Get the genre of a file (or the content of the file)
     *
     * @param in The content of the file
     * @param file The file to inpect
     * @return The genre suitable to decide how to display the file
     * @throws java.io.IOException If an error occurs while getting the content
     *                             of the file
     */
    public static Genre getGenre(InputStream in, String file) throws IOException {
        Class a = find(in, file);
        return getGenre(a);
    }

    /**
     * Get the genre of a bulk of data
     *
     * @param in A stream containing the data
     * @return The genre suitable to decide how to display the file
     * @throws java.io.IOException If an error occurs while getting the content
     */
    public static Genre getGenre(InputStream in) throws IOException {
        Class a = find(in);
        return getGenre(a);
    }

    /**
     * Get the genre for a named class (this is most likely an analyzer)
     * @param clazz the class to get the genre for
     * @return The genre of this class (null if not found)
     */
    public static Genre getGenre(Class clazz) {
        Genre g = null;
        if (clazz != null) {
            try {
                g = (Genre) clazz.getField("g").get(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return g;
    }

    /**
     * Finds a suitable analyser class for file name. If the analyzer cannot
     * be determined by the file extension, try to look at the data in the
     * InputStream to find a suitable analyzer.
     *
     * Use if you just want to find file type.
     *
     *
     * @param in The input stream containing the data
     * @param file The file name to get the analyzer for
     * @return The analyzer to use
     * @throws java.io.IOException If a problem occurs while reading the data
     */
    public static Class<? extends FileAnalyzer> find(InputStream in, String file) throws IOException {
        Class<? extends FileAnalyzer> a = find(file);
        if (a == null) {
            a = find(in);
        }
        return a;
    }

    /**
     * Finds a suitable analyser class for file name.
     *
     * @param file The file name to get the analyzer for
     * @return The analyzer to use
     */
    public static Class<? extends FileAnalyzer> find(String file) {
        int i = 0;
        if ((i = file.lastIndexOf('/')) > 0 || (i = file.lastIndexOf('\\')) > 0) {
            if (i + 1 < file.length()) {
                file = file.substring(i + 1);
            }
        }
        file = file.toUpperCase();
        int dotpos = file.lastIndexOf('.');
        if (dotpos >= 0) {
            Class<? extends FileAnalyzer> analyzer = ext.get(file.substring(dotpos + 1).toUpperCase());
            if (analyzer != null) {
                //System.err.println(path.substring(dotpos+1).toUpperCase() + " = " + analyzer.getSimpleName());
                return analyzer;
            }
        }
        // file doesn't have any of the extensions we know
        return null;
    }

    /**
     * Finds a suitable analyser class for the data in this stream
     *
     * @param in The stream containing the data to analyze
     * @return The analyzer to use
     * @throws java.io.IOException if an error occurs while reading data from
     *                             the stream
     */
    public static Class<? extends FileAnalyzer> find(InputStream in) throws IOException {
        in.mark(8);
        byte[] content = new byte[8];
        int len = in.read(content);
        in.reset();
        if (len < 4) {
            return null;
        }
        Class<? extends FileAnalyzer> a = find(content);
        if (a == null) {
            for (Method matcher : matchers) {
                try {
                    //System.out.println("USING = " + matcher.getName());
                    // cannot check conversion because of reflection
                    @SuppressWarnings(value = "unchecked")
                    Class<? extends FileAnalyzer> c = (Class) matcher.invoke(null, content);

                    if (c != null) {
                        return c;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return a;
    }

    /**
     * Finds a suitable analyser class for a magic signature
     *
     * @param signature the magic signature look up
     * @return The analyzer to use
     */
    public static Class<? extends FileAnalyzer> find(byte[] signature) {
        char[] chars = new char[signature.length > 8 ? 8 : signature.length];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (0xFF & signature[i]);
        }
        return findMagic(new String(chars));
    }

    /**
     * Get an analyzer by looking up the "magic signature"
     * @param signature the signature to look up
     * @return The analyzer to handle data with this signature
     */
    public static Class<? extends FileAnalyzer> findMagic(String signature) {
        Class<? extends FileAnalyzer> a = magics.get(signature);
        if (a == null) {
            String sigWithoutBOM = stripBOM(signature);
            for (Map.Entry<String, Class<? extends FileAnalyzer>> entry :
                     magics.entrySet()) {
                if (signature.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
                // See if text files have the magic sequence if we remove the
                // byte-order marker
                if (sigWithoutBOM != null &&
                        getGenre(entry.getValue()) == Genre.PLAIN &&
                        sigWithoutBOM.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return a;
    }

    /** Byte-order markers. */
    private static final String[] BOMS = {
        new String(new char[] { 0xEF, 0xBB, 0xBF }), // UTF-8 BOM
        new String(new char[] { 0xFE, 0xFF }),       // UTF-16BE BOM
        new String(new char[] { 0xFF, 0xFE }),       // UTF-16LE BOM
    };

    /**
     * Strip away the byte-order marker from the string, if it has one.
     *
     * @param str the string to remove the BOM from
     * @return a string without the byte-order marker, or <code>null</code> if
     * the string doesn't start with a BOM
     */
    private static String stripBOM(String str) {
        for (String bom : BOMS) {
            if (str.startsWith(bom)) {
                return str.substring(bom.length());
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        AnalyzerGuru af = new AnalyzerGuru();
        System.out.println("<pre wrap=true>");
        for (String arg : args) {
            try {
                Class<? extends FileAnalyzer> an = AnalyzerGuru.find(arg);
                File f = new File(arg);
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
                FileAnalyzer fa = AnalyzerGuru.getAnalyzer(in, arg);
                System.out.println("\nANALYZER = " + fa);
                Document doc = af.getDocument(f, in, arg);
                System.out.println("\nDOCUMENT = " + doc);

                Iterator iterator = doc.getFields().iterator();
                while (iterator.hasNext()) {
                    org.apache.lucene.document.Field field = (org.apache.lucene.document.Field) iterator.next();
                    if (field.isTokenized()) {
                        Reader r = field.readerValue();
                        if (r == null) {
                            r = new StringReader(field.stringValue());
                        }
                        TokenStream ts = fa.tokenStream(field.name(), r);
                        System.out.println("\nFIELD = " + field.name() + " TOKEN STREAM = " + ts.getClass().getName());
                        Token t;
                        while ((t = ts.next()) != null) {
                            System.out.print(t.termText());
                            System.out.print(' ');
                        }
                        System.out.println();
                    }
                    if (field.isStored()) {
                        System.out.println("\nFIELD = " + field.name());
                        if (field.readerValue() == null) {
                            System.out.println(field.stringValue());
                        } else {
                            System.out.println("STORING THE READER");
                        }
                    }
                }
                System.out.println("Writing XREF--------------");
                Writer out = new OutputStreamWriter(System.out);
                fa.writeXref(out);
                out.flush();
            } catch (Exception e) {
                System.err.println("ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
