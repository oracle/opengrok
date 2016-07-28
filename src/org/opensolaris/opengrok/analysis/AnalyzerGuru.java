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
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.util.BytesRef;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.analysis.archive.BZip2AnalyzerFactory;
import org.opensolaris.opengrok.analysis.archive.GZIPAnalyzerFactory;
import org.opensolaris.opengrok.analysis.archive.TarAnalyzerFactory;
import org.opensolaris.opengrok.analysis.archive.ZipAnalyzerFactory;
import org.opensolaris.opengrok.analysis.c.CAnalyzerFactory;
import org.opensolaris.opengrok.analysis.c.CxxAnalyzerFactory;
import org.opensolaris.opengrok.analysis.csharp.CSharpAnalyzerFactory;
import org.opensolaris.opengrok.analysis.data.IgnorantAnalyzerFactory;
import org.opensolaris.opengrok.analysis.data.ImageAnalyzerFactory;
import org.opensolaris.opengrok.analysis.document.TroffAnalyzerFactory;
import org.opensolaris.opengrok.analysis.erlang.ErlangAnalyzerFactory;
import org.opensolaris.opengrok.analysis.executables.ELFAnalyzerFactory;
import org.opensolaris.opengrok.analysis.executables.JarAnalyzerFactory;
import org.opensolaris.opengrok.analysis.executables.JavaClassAnalyzerFactory;
import org.opensolaris.opengrok.analysis.fortran.FortranAnalyzerFactory;
import org.opensolaris.opengrok.analysis.golang.GolangAnalyzerFactory;
import org.opensolaris.opengrok.analysis.haskell.HaskellAnalyzerFactory;
import org.opensolaris.opengrok.analysis.lua.LuaAnalyzerFactory;
import org.opensolaris.opengrok.analysis.java.JavaAnalyzerFactory;
import org.opensolaris.opengrok.analysis.javascript.JavaScriptAnalyzerFactory;
import org.opensolaris.opengrok.analysis.lisp.LispAnalyzerFactory;
import org.opensolaris.opengrok.analysis.pascal.PascalAnalyzerFactory;
import org.opensolaris.opengrok.analysis.perl.PerlAnalyzerFactory;
import org.opensolaris.opengrok.analysis.php.PhpAnalyzerFactory;
import org.opensolaris.opengrok.analysis.plain.PlainAnalyzerFactory;
import org.opensolaris.opengrok.analysis.plain.XMLAnalyzerFactory;
import org.opensolaris.opengrok.analysis.python.PythonAnalyzerFactory;
import org.opensolaris.opengrok.analysis.scala.ScalaAnalyzerFactory;
import org.opensolaris.opengrok.analysis.clojure.ClojureAnalyzerFactory;
import org.opensolaris.opengrok.analysis.sh.ShAnalyzerFactory;
import org.opensolaris.opengrok.analysis.sql.PLSQLAnalyzerFactory;
import org.opensolaris.opengrok.analysis.sql.SQLAnalyzerFactory;
import org.opensolaris.opengrok.analysis.tcl.TclAnalyzerFactory;
import org.opensolaris.opengrok.analysis.uue.UuencodeAnalyzerFactory;
import org.opensolaris.opengrok.analysis.vb.VBAnalyzerFactory;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.history.HistoryException;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.history.HistoryReader;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.web.Util;

/**
 * Manages and provides Analyzers as needed. Please see
 * <a href="https://github.com/OpenGrok/OpenGrok/wiki/OpenGrok-Internals">
 * this</a> page for a great description of the purpose of the AnalyzerGuru.
 *
 * Created on September 22, 2005
 *
 * @author Chandan
 */
public class AnalyzerGuru {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyzerGuru.class);

    /**
     * The default {@code FileAnalyzerFactory} instance.
     */
    private static final FileAnalyzerFactory DEFAULT_ANALYZER_FACTORY = new FileAnalyzerFactory();

    /**
     * Map from file names to analyzer factories.
     */
    private static final Map<String, FileAnalyzerFactory> FILE_NAMES = new HashMap<>();

    /**
     * Map from file extensions to analyzer factories.
     */
    private static final Map<String, FileAnalyzerFactory> ext = new HashMap<>();

    /**
     * Map from file prefixes to analyzer factories.
     */
    private static final Map<String, FileAnalyzerFactory> pre = new HashMap<>();

    // @TODO: have a comparator
    /**
     * Map from magic strings to analyzer factories.
     */
    private static final SortedMap<String, FileAnalyzerFactory> magics = new TreeMap<>();

    /**
     * List of matcher objects which can be used to determine which analyzer
     * factory to use.
     */
    private static final List<FileAnalyzerFactory.Matcher> matchers = new ArrayList<>();

    /**
     * List of all registered {@code FileAnalyzerFactory} instances.
     */
    private static final List<FileAnalyzerFactory> factories = new ArrayList<>();

    public static final Reader dummyR = new StringReader("");
    public static final String dummyS = "";
    public static final FieldType string_ft_stored_nanalyzed_norms = new FieldType(StringField.TYPE_STORED);
    public static final FieldType string_ft_nstored_nanalyzed_norms = new FieldType(StringField.TYPE_NOT_STORED);

    private static final Map<String, String> fileTypeDescriptions = new TreeMap<>();

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
            new CSharpAnalyzerFactory(),
            new VBAnalyzerFactory(),
            new CxxAnalyzerFactory(),
            new ErlangAnalyzerFactory(),
            new ShAnalyzerFactory(),
            PlainAnalyzerFactory.DEFAULT_INSTANCE,
            new UuencodeAnalyzerFactory(),
            new GZIPAnalyzerFactory(),
            new JavaAnalyzerFactory(),
            new JavaScriptAnalyzerFactory(),
            new PythonAnalyzerFactory(),
            new PerlAnalyzerFactory(),
            new PhpAnalyzerFactory(),
            new LispAnalyzerFactory(),
            new TclAnalyzerFactory(),
            new ScalaAnalyzerFactory(),
            new ClojureAnalyzerFactory(),
            new SQLAnalyzerFactory(),
            new PLSQLAnalyzerFactory(),
            new FortranAnalyzerFactory(),
            new HaskellAnalyzerFactory(),
            new GolangAnalyzerFactory(),
            new LuaAnalyzerFactory(),
            new PascalAnalyzerFactory()
        };

        for (FileAnalyzerFactory analyzer : analyzers) {
            registerAnalyzer(analyzer);
        }

        for (FileAnalyzerFactory analyzer : analyzers) {
            if (analyzer.getName() != null && !analyzer.getName().isEmpty()) {
                fileTypeDescriptions.put(analyzer.getAnalyzer().getFileTypeName(), analyzer.getName());
            }
        }

        string_ft_stored_nanalyzed_norms.setOmitNorms(false);
        string_ft_nstored_nanalyzed_norms.setOmitNorms(false);

    }

    public static Map<String, String> getfileTypeDescriptions() {
        return fileTypeDescriptions;
    }

    public List<FileAnalyzerFactory> getAnalyzerFactories() {
        return factories;
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
        for (String prefix : factory.getPrefixes()) {
            FileAnalyzerFactory old = pre.put(prefix, factory);
            assert old == null :
                    "prefix '" + prefix + "' used in multiple analyzers";
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
     * Instruct the AnalyzerGuru to use a given analyzer for a given file
     * prefix.
     *
     * @param prefix the file prefix to add
     * @param factory a factory which creates the analyzer to use for the given
     * extension (if you pass null as the analyzer, you will disable the
     * analyzer used for that extension)
     */
    public static void addPrefix(String prefix,
            FileAnalyzerFactory factory) {
        if (factory == null) {
            pre.remove(prefix);
        } else {
            pre.put(prefix, factory);
        }
    }

    /**
     * Instruct the AnalyzerGuru to use a given analyzer for a given file
     * extension.
     *
     * @param extension the file-extension to add
     * @param factory a factory which creates the analyzer to use for the given
     * extension (if you pass null as the analyzer, you will disable the
     * analyzer used for that extension)
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
     *
     * @return default FileAnalyzer
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
     * @throws java.io.IOException If an error occurs while accessing the data
     * in the input stream.
     */
    public static FileAnalyzer getAnalyzer(InputStream in, String file) throws IOException {
        FileAnalyzerFactory factory = find(in, file);
        if (factory == null) {
            return getAnalyzer();
        }
        return factory.getAnalyzer();
    }

    /**
     * Populate a Lucene document with the required fields.
     *
     * @param doc The document to populate
     * @param file The file to index
     * @param path Where the file is located (from source root)
     * @param fa The analyzer to use on the file
     * @param xrefOut Where to write the xref (possibly {@code null})
     * @throws IOException If an exception occurs while collecting the data
     */
    public void populateDocument(Document doc, File file, String path,
            FileAnalyzer fa, Writer xrefOut)
            throws IOException {
        String date = DateTools.timeToString(file.lastModified(),
                DateTools.Resolution.MILLISECOND);
        doc.add(new Field(QueryBuilder.U, Util.path2uid(path, date),
                string_ft_stored_nanalyzed_norms));
        doc.add(new Field(QueryBuilder.FULLPATH, file.getAbsolutePath(),
                string_ft_nstored_nanalyzed_norms));
        doc.add(new SortedDocValuesField(QueryBuilder.FULLPATH, new BytesRef(file.getAbsolutePath())));

        try {
            HistoryReader hr = HistoryGuru.getInstance().getHistoryReader(file);
            if (hr != null) {
                doc.add(new TextField(QueryBuilder.HIST, hr));
                // date = hr.getLastCommentDate() //RFE
            }
        } catch (HistoryException e) {
            LOGGER.log(Level.WARNING, "An error occurred while reading history: ", e);
        }
        doc.add(new Field(QueryBuilder.DATE, date, string_ft_stored_nanalyzed_norms));
        doc.add(new SortedDocValuesField(QueryBuilder.DATE, new BytesRef(date)));
        if (path != null) {
            doc.add(new TextField(QueryBuilder.PATH, path, Store.YES));
            Project project = Project.getProject(path);
            if (project != null) {
                doc.add(new TextField(QueryBuilder.PROJECT, project.getPath(), Store.YES));
            }
        }

        if (fa != null) {
            Genre g = fa.getGenre();
            if (g == Genre.PLAIN || g == Genre.XREFABLE || g == Genre.HTML) {
                doc.add(new Field(QueryBuilder.T, g.typeName(), string_ft_stored_nanalyzed_norms
                ));
            }
            fa.analyze(doc, StreamSource.fromFile(file), xrefOut);

            String type = fa.getFileTypeName();
            doc.add(new StringField(QueryBuilder.TYPE, type, Store.YES));
        }
    }

    /**
     * Get the content type for a named file.
     *
     * @param in The input stream we want to get the content type for (if we
     * cannot determine the content type by the filename)
     * @param file The name of the file
     * @return The contentType suitable for printing to
     * response.setContentType() or null if the factory was not found
     * @throws java.io.IOException If an error occurs while accessing the input
     * stream.
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
     * Write a browse-able version of the file
     *
     * @param factory The analyzer factory for this file type
     * @param in The input stream containing the data
     * @param out Where to write the result
     * @param defs definitions for the source file, if available
     * @param annotation Annotation information for the file
     * @param project Project the file belongs to
     * @throws java.io.IOException If an error occurs while creating the output
     */
    public static void writeXref(FileAnalyzerFactory factory, Reader in,
            Writer out, Definitions defs,
            Annotation annotation, Project project)
            throws IOException {
        Reader input = in;
        if (factory.getGenre() == Genre.PLAIN) {
            // This is some kind of text file, so we need to expand tabs to
            // spaces to match the project's tab settings.
            input = ExpandTabsReader.wrap(in, project);
        }
        factory.writeXref(input, out, defs, annotation, project);
    }

    /**
     * Get the genre of a file
     *
     * @param file The file to inspect
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
     *
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
            InstantiationException {
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
    private static FileAnalyzerFactory findFactory(Class<?> factoryClass)
            throws InstantiationException, IllegalAccessException {
        for (FileAnalyzerFactory f : factories) {
            if (f.getClass() == factoryClass) {
                return f;
            }
        }
        FileAnalyzerFactory f
                = (FileAnalyzerFactory) factoryClass.newInstance();
        registerAnalyzer(f);
        return f;
    }

    /**
     * Finds a suitable analyser class for file name. If the analyzer cannot be
     * determined by the file extension, try to look at the data in the
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
            throws IOException {
        FileAnalyzerFactory factory = find(file);
        // TODO above is not that great, since if 2 analyzers share one extension
        // then only the first one registered will own it
        // it would be cool if above could return more analyzers and below would
        // then decide between them ...
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
        int i;

        // Get basename of the file first.
        if (((i = path.lastIndexOf(File.separatorChar)) > 0)
                && (i + 1 < path.length())) {
            path = path.substring(i + 1);
        }

        int dotpos = path.lastIndexOf('.');
        if (dotpos >= 0) {
            FileAnalyzerFactory factory;

            // Try matching the prefix.
            if (dotpos > 0) {
                factory
                        = pre.get(path.substring(0, dotpos).toUpperCase(Locale.getDefault()));
                if (factory != null) {
                    return factory;
                }
            }

            // Now try matching the suffix. We kind of consider this order (first
            // prefix then suffix) to be workable although for sure there can be
            // cases when this does not work.
            factory
                    = ext.get(path.substring(dotpos + 1).toUpperCase(Locale.getDefault()));
            if (factory != null) {
                return factory;
            }
        }

        // file doesn't have any of the prefix or extensions we know, try full match
        return FILE_NAMES.get(path.toUpperCase(Locale.getDefault()));
    }

    /**
     * Finds a suitable analyzer class for the data in this stream
     *
     * @param in The stream containing the data to analyze
     * @return the analyzer factory to use
     * @throws java.io.IOException if an error occurs while reading data from
     * the stream
     */
    public static FileAnalyzerFactory find(InputStream in) throws IOException {
        in.mark(8);
        byte[] content = new byte[8];
        int len = in.read(content);
        in.reset();

        if (len < 8) {
            /*
             * Need at least 4 bytes to perform magic string matching.
             */
            if (len < 4) {
                return null;
            }
            content = Arrays.copyOf(content, len);
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
     * Finds a suitable analyzer class for a magic signature
     *
     * @param signature the magic signature look up
     * @return the analyzer factory to use
     */
    private static FileAnalyzerFactory find(byte[] signature)
            throws IOException {
        // XXX this assumes ISO-8859-1 encoding (and should work in most cases
        // for US-ASCII, UTF-8 and other ISO-8859-* encodings, but not always),
        // we should try to be smarter than this...
        char[] chars = new char[signature.length > 8 ? 8 : signature.length];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (0xFF & signature[i]);
        }

        String sig = new String(chars);

        FileAnalyzerFactory a = magics.get(sig);
        if (a == null) {
            String sigWithoutBOM = stripBOM(signature);
            for (Map.Entry<String, FileAnalyzerFactory> entry
                    : magics.entrySet()) {
                if (sig.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
                // See if text files have the magic sequence if we remove the
                // byte-order marker
                if (sigWithoutBOM != null
                        && entry.getValue().getGenre() == Genre.PLAIN
                        && sigWithoutBOM.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return a;
    }

    /**
     * Byte-order markers.
     */
    private static final Map<String, byte[]> BOMS
            = new HashMap<>();

    static {
        BOMS.put("UTF-8", new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        BOMS.put("UTF-16BE", new byte[]{(byte) 0xFE, (byte) 0xFF});
        BOMS.put("UTF-16LE", new byte[]{(byte) 0xFF, (byte) 0xFE});
    }

    /**
     * Strip away the byte-order marker from the string, if it has one.
     *
     * @param sig a sequence of bytes from which to remove the BOM
     * @return a string without the byte-order marker, or <code>null</code> if
     * the string doesn't start with a BOM
     * @throws java.io.IOException in case of any read error
     */
    public static String stripBOM(byte[] sig) throws IOException {
        for (Map.Entry<String, byte[]> entry : BOMS.entrySet()) {
            String encoding = entry.getKey();
            byte[] bom = entry.getValue();
            if (sig.length > bom.length) {
                int i = 0;
                while (i < bom.length && sig[i] == bom[i]) {
                    i++;
                }
                if (i == bom.length) {
                    // BOM matched beginning of signature
                    return new String(
                            sig,
                            bom.length, // offset
                            sig.length - bom.length, // length
                            encoding);
                }
            }
        }
        return null;
    }
}
