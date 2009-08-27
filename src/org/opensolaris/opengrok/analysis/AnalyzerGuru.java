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
 * Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.analysis.archive.BZip2AnalyzerFactory;
import org.opensolaris.opengrok.analysis.archive.GZIPAnalyzerFactory;
import org.opensolaris.opengrok.analysis.archive.TarAnalyzerFactory;
import org.opensolaris.opengrok.analysis.archive.ZipAnalyzerFactory;
import org.opensolaris.opengrok.analysis.c.CAnalyzerFactory;
import org.opensolaris.opengrok.analysis.c.CxxAnalyzerFactory;
import org.opensolaris.opengrok.analysis.data.IgnorantAnalyzerFactory;
import org.opensolaris.opengrok.analysis.data.ImageAnalyzerFactory;
import org.opensolaris.opengrok.analysis.document.TroffAnalyzerFactory;
import org.opensolaris.opengrok.analysis.executables.ELFAnalyzerFactory;
import org.opensolaris.opengrok.analysis.executables.JarAnalyzerFactory;
import org.opensolaris.opengrok.analysis.executables.JavaClassAnalyzerFactory;
import org.opensolaris.opengrok.analysis.java.JavaAnalyzerFactory;
import org.opensolaris.opengrok.analysis.lisp.LispAnalyzerFactory;
import org.opensolaris.opengrok.analysis.plain.PlainAnalyzerFactory;
import org.opensolaris.opengrok.analysis.plain.XMLAnalyzerFactory;
import org.opensolaris.opengrok.analysis.sh.ShAnalyzerFactory;
import org.opensolaris.opengrok.analysis.sql.SQLAnalyzerFactory;
import org.opensolaris.opengrok.analysis.tcl.TclAnalyzerFactory;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.history.HistoryException;
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

    /** The default {@code FileAnalyzerFactory} instance. */
    private static final FileAnalyzerFactory
        DEFAULT_ANALYZER_FACTORY = new FileAnalyzerFactory();

    /** Map from file names to analyzer factories. */
    private static final Map<String, FileAnalyzerFactory>
        FILE_NAMES = new HashMap<String, FileAnalyzerFactory>();

    /** Map from file extensions to analyzer factories. */
    private static final Map<String, FileAnalyzerFactory>
        ext = new HashMap<String, FileAnalyzerFactory>();

    // @TODO: have a comparator
    /** Map from magic strings to analyzer factories. */
    private static final SortedMap<String, FileAnalyzerFactory>
        magics = new TreeMap<String, FileAnalyzerFactory>();

    /**
     * List of matcher objects which can be used to determine which analyzer
     * factory to use.
     */
    private static final List<FileAnalyzerFactory.Matcher>
        matchers = new ArrayList<FileAnalyzerFactory.Matcher>();

    /** List of all registered {@code FileAnalyzerFactory} instances. */
    private static final List<FileAnalyzerFactory>
        factories = new ArrayList<FileAnalyzerFactory>();

    /*
     * If you write your own analyzer please register it here
     */
    static {
        FileAnalyzerFactory[] analyzers = {
            DEFAULT_ANALYZER_FACTORY,
            new IgnorantAnalyzerFactory(),
            new BZip2AnalyzerFactory(),
            new XMLAnalyzerFactory(),
            new TroffAnalyzerFactory(),
            new ELFAnalyzerFactory(),
            new JavaClassAnalyzerFactory(),
            new ImageAnalyzerFactory(),
            JarAnalyzerFactory.DEFAULT_INSTANCE,
            ZipAnalyzerFactory.DEFAULT_INSTANCE,
            new TarAnalyzerFactory(),
            new CAnalyzerFactory(),
            new CxxAnalyzerFactory(),
            new ShAnalyzerFactory(),
            PlainAnalyzerFactory.DEFAULT_INSTANCE,
            new GZIPAnalyzerFactory(),
            new JavaAnalyzerFactory(),
            new LispAnalyzerFactory(),
            new TclAnalyzerFactory(),
            new SQLAnalyzerFactory(),
        };

        for (FileAnalyzerFactory analyzer : analyzers) {
            registerAnalyzer(analyzer);
        }
    }

    /**
     * Register a {@code FileAnalyzerFactory} instance.
     */
    private static void registerAnalyzer(FileAnalyzerFactory factory) {
        for (String name : factory.getFileNames()) {
            FileAnalyzerFactory old = FILE_NAMES.put(name, factory);
            assert old == null :
                "name '" + name + "' used in multiple analyzers";
        }
        for (String suffix : factory.getSuffixes()) {
            FileAnalyzerFactory old = ext.put(suffix, factory);
            assert old == null :
            "suffix '" + suffix + "' used in multiple analyzers";
        }
        for (String magic : factory.getMagicStrings()) {
            FileAnalyzerFactory old = magics.put(magic, factory);
            assert old == null :
                "magic '" + magic + "' used in multiple analyzers";
        }
        matchers.addAll(factory.getMatchers());
        factories.add(factory);
    }

    /**
     *  Instruct the AnalyzerGuru to use a given analyzer for a given
     *  file extension.
     *  @param extension the file-extension to add
     *  @param factory   a factory which creates
     *                   the analyzer to use for the given extension
     *                  (if you pass null as the analyzer, you will disable
     *                   the analyzer used for that extension)
     */
    public static void addExtension(String extension,
                                    FileAnalyzerFactory factory) {
        if (factory == null) {
            ext.remove(extension);
        } else {
            ext.put(extension, factory);
        }
    }

    /**
     * Get the default Analyzer.
     */
    public static FileAnalyzer getAnalyzer() {
        return DEFAULT_ANALYZER_FACTORY.getAnalyzer();
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
        FileAnalyzerFactory factory = find(in, file);
        if (factory == null) {
            return getAnalyzer();
        }
        return factory.getAnalyzer();
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
    public Document getDocument(File file, InputStream in, String path,
                                FileAnalyzer fa) throws IOException {
        Document doc = new Document();
        String date = DateTools.timeToString(file.lastModified(), DateTools.Resolution.MILLISECOND);
        doc.add(new Field("u", Util.uid(path, date), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field("fullpath", file.getAbsolutePath(), Field.Store.YES, Field.Index.ANALYZED));

        try {
            HistoryReader hr = HistoryGuru.getInstance().getHistoryReader(file);
            if (hr != null) {
                doc.add(new Field("hist", hr));
                // date = hr.getLastCommentDate() //RFE
            }
        } catch (HistoryException e) {
            OpenGrokLogger.getLogger().log(Level.WARNING, "An error occurred while reading history: ", e);
        }
        doc.add(new Field("date", date, Field.Store.YES, Field.Index.NOT_ANALYZED));
        if (path != null) {
            doc.add(new Field("path", path, Field.Store.YES, Field.Index.ANALYZED));
            Project project = Project.getProject(path);
            if (project != null) {
                doc.add(new Field("project", project.getPath(), Field.Store.YES, Field.Index.ANALYZED));
            }
        }

        if (fa != null) {
            Genre g = fa.getGenre();
            if (g == Genre.PLAIN) {
                doc.add(new Field("t", "p", Field.Store.YES, Field.Index.NOT_ANALYZED));
            } else if (g == Genre.XREFABLE) {
                doc.add(new Field("t", "x", Field.Store.YES, Field.Index.NOT_ANALYZED));
            } else if (g == Genre.HTML) {
                doc.add(new Field("t", "h", Field.Store.YES, Field.Index.NOT_ANALYZED));
            }
            fa.analyze(doc, in);
        }
        doc.removeField("fullpath");

        return doc;
    }

    /**
     * Get the content type for a named file.
     *
     * @param in The input stream we want to get the content type for (if
     *           we cannot determine the content type by the filename)
     * @param file The name of the file
     * @return The contentType suitable for printing to response.setContentType() or null
     *         if the factory was not found
     * @throws java.io.IOException If an error occurs while accessing the input
     *                             stream.
     */
    public static String getContentType(InputStream in, String file) throws IOException {
        FileAnalyzerFactory factory = find(in, file);
        String type = null;
        if (factory != null) {
            type = factory.getContentType();
        }
        return type;
    }

    /**
     * Write a browsable version of the file
     *
     * @param factory The analyzer factory for this filetype
     * @param in The input stream containing the data
     * @param out Where to write the result
     * @param annotation Annotation information for the file
     * @param project Project the file belongs to
     * @throws java.io.IOException If an error occurs while creating the
     *                             output
     */
    public static void writeXref(FileAnalyzerFactory factory, InputStream in,
                                 Writer out, Annotation annotation, Project project)
        throws IOException
    {
        factory.writeXref(in, out, annotation, project);
    }

    /**
     * Get the genre of a file
     *
     * @param file The file to inpect
     * @return The genre suitable to decide how to display the file
     */
    public static Genre getGenre(String file) {
        return getGenre(find(file));
    }

    /**
     * Get the genre of a bulk of data
     *
     * @param in A stream containing the data
     * @return The genre suitable to decide how to display the file
     * @throws java.io.IOException If an error occurs while getting the content
     */
    public static Genre getGenre(InputStream in) throws IOException {
        return getGenre(find(in));
    }

    /**
     * Get the genre for a named class (this is most likely an analyzer)
     * @param factory the analyzer factory to get the genre for
     * @return The genre of this class (null if not found)
     */
    public static Genre getGenre(FileAnalyzerFactory factory) {
        if (factory != null) {
            return factory.getGenre();
        }
        return null;
    }

    /**
     * Find a {@code FileAnalyzerFactory} with the specified class name. If one
     * doesn't exist, create one and register it.
     *
     * @param factoryClassName name of the factory class
     * @return a file analyzer factory
     *
     * @throws ClassNotFoundException if there is no class with that name
     * @throws ClassCastException if the class is not a subclass of {@code
     * FileAnalyzerFactory}
     * @throws IllegalAccessException if the constructor cannot be accessed
     * @throws InstantiationException if the class cannot be instantiated
     */
    public static FileAnalyzerFactory findFactory(String factoryClassName)
        throws ClassNotFoundException, IllegalAccessException,
               InstantiationException
    {
        return findFactory(Class.forName(factoryClassName));
    }

    /**
     * Find a {@code FileAnalyzerFactory} which is an instance of the specified
     * class. If one doesn't exist, create one and register it.
     *
     * @param factoryClass the factory class
     * @return a file analyzer factory
     *
     * @throws ClassCastException if the class is not a subclass of {@code
     * FileAnalyzerFactory}
     * @throws IllegalAccessException if the constructor cannot be accessed
     * @throws InstantiationException if the class cannot be instantiated
     */
    private static FileAnalyzerFactory findFactory(Class factoryClass)
        throws InstantiationException, IllegalAccessException
    {
        for (FileAnalyzerFactory f : factories) {
            if (f.getClass() == factoryClass) {
                return f;
            }
        }
        FileAnalyzerFactory f =
            (FileAnalyzerFactory) factoryClass.newInstance();
        registerAnalyzer(f);
        return f;
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
     * @return the analyzer factory to use
     * @throws java.io.IOException If a problem occurs while reading the data
     */
    public static FileAnalyzerFactory find(InputStream in, String file)
        throws IOException
    {
        FileAnalyzerFactory factory = find(file);
        if (factory != null) {
            return factory;
        }
        return find(in);
    }

    /**
     * Finds a suitable analyser class for file name.
     *
     * @param file The file name to get the analyzer for
     * @return the analyzer factory to use
     */
    public static FileAnalyzerFactory find(String file) {
        String path = file;
        int i = 0;
        if (((i = path.lastIndexOf('/')) > 0 || (i = path.lastIndexOf('\\')) > 0) 
            && (i + 1 < path.length())) {
            path = path.substring(i + 1);
        }
        int dotpos = path.lastIndexOf('.');
        if (dotpos >= 0) {
            FileAnalyzerFactory factory =
                ext.get(path.substring(dotpos + 1).toUpperCase(Locale.US));
            if (factory != null) {
                return factory;
            }
        }
        // file doesn't have any of the extensions we know, try full match
        return FILE_NAMES.get(path.toUpperCase(Locale.US));
    }

    /**
     * Finds a suitable analyser class for the data in this stream
     *
     * @param in The stream containing the data to analyze
     * @return the analyzer factory to use
     * @throws java.io.IOException if an error occurs while reading data from
     *                             the stream
     */
    public static FileAnalyzerFactory find(InputStream in) throws IOException {
        in.mark(8);
        byte[] content = new byte[8];
        int len = in.read(content);
        in.reset();
        if (len < 4) {
            return null;
        }

        FileAnalyzerFactory factory = find(content);
        if (factory != null) {
            return factory;
        }

        for (FileAnalyzerFactory.Matcher matcher : matchers) {
            FileAnalyzerFactory fac = matcher.isMagic(content, in);
            if (fac != null) {
                return fac;
            }
        }

        return null;
    }

    /**
     * Finds a suitable analyser class for a magic signature
     *
     * @param signature the magic signature look up
     * @return the analyzer factory to use
     */
    public static FileAnalyzerFactory find(byte[] signature) {
        char[] chars = new char[signature.length > 8 ? 8 : signature.length];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (0xFF & signature[i]);
        }
        return findMagic(new String(chars));
    }

    /**
     * Get an analyzer by looking up the "magic signature"
     * @param signature the signature to look up
     * @return the analyzer factory to handle data with this signature
     */
    public static FileAnalyzerFactory findMagic(String signature) {
        FileAnalyzerFactory a = magics.get(signature);
        if (a == null) {
            String sigWithoutBOM = stripBOM(signature);
            for (Map.Entry<String, FileAnalyzerFactory> entry :
                     magics.entrySet()) {
                if (signature.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
                // See if text files have the magic sequence if we remove the
                // byte-order marker
                if (sigWithoutBOM != null &&
                        entry.getValue().getGenre() == Genre.PLAIN &&
                        sigWithoutBOM.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return a;
    }

    /** Byte-order markers. */
    private static final String[] BOMS = {
        new String(new char[] {0xEF, 0xBB, 0xBF}), // UTF-8 BOM
        new String(new char[] {0xFE, 0xFF}),       // UTF-16BE BOM
        new String(new char[] {0xFF, 0xFE}),       // UTF-16LE BOM
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
}
