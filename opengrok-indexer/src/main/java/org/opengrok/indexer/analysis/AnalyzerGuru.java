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
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.Annotation;
import org.opengrok.indexer.history.HistoryException;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.HistoryReader;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.web.Util;

/**
 * Manages and provides Analyzers as needed. Please see
 * <a href="https://github.com/OpenGrok/OpenGrok/wiki/OpenGrok-Internals">
 * this</a> page for a great description of the purpose of the AnalyzerGuru.
 * <p>
 * Created on September 22, 2005
 *
 * @author Chandan
 */
public class AnalyzerGuru {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyzerGuru.class);

    /**
     * The maximum number of characters (multi-byte if a BOM is identified) to
     * read from the input stream to be used for magic string matching
     */
    private static final int OPENING_MAX_CHARS = 100;
    /**
     * Set to 16K -- though debugging shows it would do with only 8K+3
     * (standard buffer for Java BufferedInputStream plus 3 bytes for largest
     * UTF BOM)
     */
    private static final int MARK_READ_LIMIT = 1024 * 16;
    /**
     * The number of bytes read from the start of the file for magic number or
     * string analysis. Some {@link FileAnalyzerFactory.Matcher}
     * implementations may read more data subsequently, but this field defines
     * the number of bytes initially read for general matching.
     */
    private static final int MAGIC_BYTES_NUM = 8;

    /**
     * Names of all analysis packages.
     */
    private static final List<String> analysisPkgNames = new ArrayList<>();

    public static final FieldType string_ft_stored_nanalyzed_norms = new FieldType(StringField.TYPE_STORED);
    public static final FieldType string_ft_nstored_nanalyzed_norms = new FieldType(StringField.TYPE_NOT_STORED);

    /*
     * If you write your own analyzer please register it here. The order is
     * important for any factory that uses a FileAnalyzerFactory.Matcher
     * implementation, as those are run in the same order as defined below --
     * though precise Matchers are run before imprecise ones.
     */
    static {
        try {
            string_ft_stored_nanalyzed_norms.setOmitNorms(false);
            string_ft_nstored_nanalyzed_norms.setOmitNorms(false);
        } catch (IllegalStateException t) {
            LOGGER.log(Level.SEVERE,
                    "exception hit when constructing AnalyzerGuru static", t);
            throw t;
        }
    }

    private final AnalyzerFramework pluginFramework;

    /**
     * Create a new instance of analyzer guru with default analyzers.
     */
    public AnalyzerGuru() {
        pluginFramework = new AnalyzerFramework(null);
    }

    /**
     * Create a new instance of analyzer guru with default instance and loading other analyzers from a plugin directory.
     *
     * @param pluginDirectory path to the plugin directory
     */
    public AnalyzerGuru(String pluginDirectory) {
        pluginFramework = new AnalyzerFramework(pluginDirectory);
    }

    /**
     * Gets a version number to be used to tag documents examined by the guru so
     * that {@link AbstractAnalyzer} selection can be re-done later if a stored
     * version number is different from the current implementation or if guru
     * factory registrations are modified by the user to change the guru
     * operation.
     * <p>
     * The static part of the version is bumped in a release when e.g. new
     * {@link FileAnalyzerFactory} subclasses are registered or when existing
     * {@link FileAnalyzerFactory} subclasses are revised to target more or
     * different files.
     *
     * @return a value whose lower 32-bits are a static value
     * 20190211_00
     * for the current implementation and whose higher-32 bits are non-zero if
     * {@link #addExtension(String, AnalyzerFactory)}
     * or
     * {@link #addPrefix(String, AnalyzerFactory)}
     * has been called.
     */
    public long getVersionNo() {
        final int ver32 = 20190211_00; // Edit comment above too!
        long ver = ver32;
        final int customizations = Objects.hash(this.pluginFramework.getAnalyzersInfo().customizations.toArray());
        if (customizations != 0) {
            ver |= (long) customizations << 32;
        }
        return ver;
    }

    /**
     * Gets a version number according to a registered
     * {@link FileAnalyzer#getVersionNo()} for a {@code fileTypeName} according
     * to {@link FileAnalyzer#getFileTypeName()}.
     *
     * @param fileTypeName a defined instance
     * @return a registered value or {@link Long#MIN_VALUE} if
     * {@code fileTypeName} is unknown
     */
    public long getAnalyzerVersionNo(String fileTypeName) {
        return this.pluginFramework.getAnalyzersInfo().analyzerVersions.getOrDefault(fileTypeName, Long.MIN_VALUE);
    }

    public Map<String, Long> getAnalyzersVersionNos() {
        return this.pluginFramework.getAnalyzersInfo().analyzerVersions;
    }

    public Map<String, AnalyzerFactory> getExtensionsMap() {
        return this.pluginFramework.getAnalyzersInfo().extensions;
    }

    public Map<String, AnalyzerFactory> getPrefixesMap() {
        return this.pluginFramework.getAnalyzersInfo().prefixes;
    }

    public Map<String, AnalyzerFactory> getMagicsMap() {
        return this.pluginFramework.getAnalyzersInfo().magics;
    }

    public List<FileAnalyzerFactory.Matcher> getAnalyzerFactoryMatchers() {
        return this.pluginFramework.getAnalyzersInfo().matchers;
    }

    public Map<String, String> getfileTypeDescriptions() {
        return this.pluginFramework.getAnalyzersInfo().fileTypeDescriptions;
    }

    /**
     * Get the genre of a file.
     *
     * @param file The file to inspect
     * @return The genre suitable to decide how to display the file
     */
    public AbstractAnalyzer.Genre getGenre(String file) {
        return getGenre(find(file));
    }

    /**
     * Get the genre of a bulk of data.
     *
     * @param in A stream containing the data
     * @return The genre suitable to decide how to display the file
     * @throws java.io.IOException If an error occurs while getting the content
     */
    public AbstractAnalyzer.Genre getGenre(InputStream in) throws IOException {
        return getGenre(find(in));
    }

    /**
     * Get the genre for a named class (this is most likely an analyzer)
     *
     * @param factory the analyzer factory to get the genre for
     * @return The genre of this class (null if not found)
     */
    public static AbstractAnalyzer.Genre getGenre(AnalyzerFactory factory) {
        if (factory != null) {
            return factory.getGenre();
        }
        return null;
    }

    /**
     * Finds a {@code FileAnalyzerFactory} for the specified
     * {@link FileAnalyzer#getFileTypeName()}.
     *
     * @param fileTypeName a defined instance
     * @return a defined instance or {@code null}
     */
    public AnalyzerFactory findByFileTypeName(String fileTypeName) {
        return this.pluginFramework.getAnalyzersInfo().filetypeFactories.get(fileTypeName);
    }

    /**
     * Find a {@code FileAnalyzerFactory} with the specified class name. If one
     * doesn't exist, create one and register it. Allow specification of either
     * the complete class name (which includes the package name) or the simple
     * name of the class.
     *
     * @param factoryClassName name of the factory class
     * @return a file analyzer factory
     * @throws ClassNotFoundException    if there is no class with that name
     * @throws ClassCastException        if the class is not a subclass of {@code
     *                                   FileAnalyzerFactory}
     * @throws IllegalAccessException    if the constructor cannot be accessed
     * @throws InstantiationException    if the class cannot be instantiated
     * @throws NoSuchMethodException     if no-argument constructor could not be found
     * @throws InvocationTargetException if the underlying constructor throws an exception
     */
    public AnalyzerFactory findFactory(String factoryClassName)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException,
            InvocationTargetException {
        Class<?> fcn = null;
        try {
            fcn = Class.forName(factoryClassName);
        } catch (ClassNotFoundException e) {
            fcn = getFactoryClass(factoryClassName);

            if (fcn == null) {
                throw new ClassNotFoundException("Unable to locate class " + factoryClassName);
            }
        }

        return findFactory(fcn);
    }

    /**
     * Get Analyzer factory class using class simple name.
     *
     * @param simpleName which may be either the factory class
     *                   simple name (eg. CAnalyzerFactory), the analyzer name
     *                   (eg. CAnalyzer), or the language name (eg. C)
     * @return the analyzer factory class, or null when not found.
     */
    public Class<?> getFactoryClass(String simpleName) {
        Class<?> factoryClass = null;

        // Build analysis package name list first time only
        if (analysisPkgNames.isEmpty()) {
            Package[] p = Package.getPackages();
            for (Package pp : p) {
                String pname = pp.getName();
                if (pname.indexOf(".analysis.") != -1) {
                    analysisPkgNames.add(pname);
                }
            }
        }

        // This allows user to enter the language or analyzer name
        // (eg. C or CAnalyzer vs. CAnalyzerFactory)
        // Note that this assumes a regular naming scheme of
        // all language parsers:
        //      <language>Analyzer, <language>AnalyzerFactory

        if (simpleName.indexOf("Analyzer") == -1) {
            simpleName += "Analyzer";
        }

        if (simpleName.indexOf("Factory") == -1) {
            simpleName += "Factory";
        }

        for (String aPackage : analysisPkgNames) {
            try {
                String fqn = aPackage + "." + simpleName;
                factoryClass = Class.forName(fqn);
                break;
            } catch (ClassNotFoundException e) {
                // Ignore
            }
        }

        for (AnalyzerFactory factory : this.pluginFramework.getAnalyzersInfo().factories) {
            if (factory.getClass().getSimpleName().equals(simpleName)) {
                factoryClass = factory.getClass();
                break;
            }
        }

        return factoryClass;
    }

    /**
     * Find a {@code FileAnalyzerFactory} which is an instance of the specified
     * class. If one doesn't exist, create one and register it.
     *
     * @param factoryClass the factory class
     * @return a file analyzer factory
     * @throws ClassCastException        if the class is not a subclass of {@code
     *                                   FileAnalyzerFactory}
     * @throws IllegalAccessException    if the constructor cannot be accessed
     * @throws InstantiationException    if the class cannot be instantiated
     * @throws NoSuchMethodException     if no-argument constructor could not be found
     * @throws InvocationTargetException if the underlying constructor throws an exception
     */
    private AnalyzerFactory findFactory(Class<?> factoryClass)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        for (AnalyzerFactory f : this.pluginFramework.getAnalyzersInfo().factories) {
            if (f.getClass() == factoryClass) {
                return f;
            }
        }
        AnalyzerFactory f = (AnalyzerFactory) factoryClass.getDeclaredConstructor().newInstance();
        pluginFramework.registerAnalyzer(f);
        return f;
    }

    /**
     * Finds a suitable analyser class for file name. If the analyzer cannot be
     * determined by the file extension, try to look at the data in the
     * InputStream to find a suitable analyzer.
     * <p>
     * Use if you just want to find file type.
     *
     * @param in   The input stream containing the data
     * @param file The file name to get the analyzer for
     * @return the analyzer factory to use
     * @throws IOException If a problem occurs while reading the data
     */
    public AnalyzerFactory find(InputStream in, String file)
            throws IOException {
        AnalyzerFactory factory = find(file);
        // TODO above is not that great, since if 2 analyzers share one extension
        // then only the first one registered will own it
        // it would be cool if above could return more analyzers and below would
        // then decide between them ...
        if (factory != null) {
            return factory;
        }
        return findForStream(in, file);
    }

    /**
     * Finds a suitable analyser class for file name.
     *
     * @param file The file name to get the analyzer for
     * @return the analyzer factory to use
     */
    public AnalyzerFactory find(String file) {
        String path = file;
        int i;

        // Get basename of the file first.
        if (((i = path.lastIndexOf(File.separatorChar)) > 0)
                && (i + 1 < path.length())) {
            path = path.substring(i + 1);
        }

        int dotpos = path.lastIndexOf('.');
        if (dotpos >= 0) {
            AnalyzerFactory factory;

            // Try matching the prefix.
            if (dotpos > 0) {
                factory = this.pluginFramework.getAnalyzersInfo().prefixes.get(path.substring(0, dotpos).toUpperCase(Locale.ROOT));
                if (factory != null) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.log(Level.FINER, "{0}: chosen by prefix: {1}",
                                new Object[]{file,
                                        factory.getClass().getSimpleName()});
                    }
                    return factory;
                }
            }

            // Now try matching the suffix. We kind of consider this order (first
            // prefix then suffix) to be workable although for sure there can be
            // cases when this does not work.
            factory = this.pluginFramework.getAnalyzersInfo().extensions.get(path.substring(dotpos + 1).toUpperCase(Locale.ROOT));
            if (factory != null) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.log(Level.FINER, "{0}: chosen by suffix: {1}",
                            new Object[]{file,
                                    factory.getClass().getSimpleName()});
                }
                return factory;
            }
        }

        // file doesn't have any of the prefix or extensions we know, try full match
        return this.pluginFramework.getAnalyzersInfo().fileNames.get(path.toUpperCase(Locale.ROOT));
    }

    /**
     * Finds a suitable analyzer class for the data in this stream
     *
     * @param in The stream containing the data to analyze
     * @return the analyzer factory to use
     * @throws IOException if an error occurs while reading data from
     *                     the stream
     */
    public AnalyzerFactory find(InputStream in) throws IOException {
        return findForStream(in, "<anonymous>");
    }

    /**
     * Finds a suitable analyzer class for the data in this stream
     * corresponding to a file of the specified name
     *
     * @param in   The stream containing the data to analyze
     * @param file The file name to get the analyzer for
     * @return the analyzer factory to use
     * @throws IOException if an error occurs while reading data from
     *                     the stream
     */
    private AnalyzerFactory findForStream(InputStream in,
            String file) throws IOException {

        in.mark(MAGIC_BYTES_NUM);
        byte[] content = new byte[MAGIC_BYTES_NUM];
        int len = in.read(content);
        in.reset();

        if (len < MAGIC_BYTES_NUM) {
            /*
             * Need at least 4 bytes to perform magic string matching.
             */
            if (len < 4) {
                return null;
            }
            content = Arrays.copyOf(content, len);
        }

        AnalyzerFactory fac;

        // First, do precise-magic Matcher matching
        for (FileAnalyzerFactory.Matcher matcher : this.pluginFramework.getAnalyzersInfo().matchers) {
            if (matcher.getIsPreciseMagic()) {
                fac = matcher.isMagic(content, in);
                if (fac != null) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.log(Level.FINER,
                                "{0}: chosen by precise magic: {1}", new Object[]{
                                        file, fac.getClass().getSimpleName()});
                    }
                    return fac;
                }
            }
        }

        // Next, look for magic strings
        String opening = readOpening(in, content);
        fac = findMagicString(opening, file);
        if (fac != null) {
            return fac;
        }

        // Last, do imprecise-magic Matcher matching
        for (FileAnalyzerFactory.Matcher matcher : this.pluginFramework.getAnalyzersInfo().matchers) {
            if (!matcher.getIsPreciseMagic()) {
                fac = matcher.isMagic(content, in);
                if (fac != null) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.log(Level.FINER,
                                "{0}: chosen by imprecise magic: {1}",
                                new Object[]{file,
                                        fac.getClass().getSimpleName()});
                    }
                    return fac;
                }
            }
        }

        return null;
    }

    private AnalyzerFactory findMagicString(String opening,
            String file)
            throws IOException {

        // first, try to look up two words in magics
        String fragment = getWords(opening, 2);
        AnalyzerFactory fac = this.pluginFramework.getAnalyzersInfo().magics.get(fragment);
        if (fac != null) {
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.log(Level.FINER, "{0}: chosen by magic {2}: {1}",
                        new Object[]{file, fac.getClass().getSimpleName(),
                                fragment});
            }
            return fac;
        }

        // second, try to look up one word in magics
        fragment = getWords(opening, 1);
        fac = this.pluginFramework.getAnalyzersInfo().magics.get(fragment);
        if (fac != null) {
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.log(Level.FINER, "{0}: chosen by magic {2}: {1}",
                        new Object[]{file, fac.getClass().getSimpleName(),
                                fragment});
            }
            return fac;
        }

        // try to match initial substrings (DESC strlen)
        for (Map.Entry<String, AnalyzerFactory> entry :
                this.pluginFramework.getAnalyzersInfo().magics.entrySet()) {
            String magic = entry.getKey();
            if (opening.startsWith(magic)) {
                fac = entry.getValue();
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.log(Level.FINER,
                            "{0}: chosen by magic(substr) {2}: {1}", new Object[]{
                                    file, fac.getClass().getSimpleName(), magic});
                }
                return fac;
            }
        }

        return null;
    }

    /**
     * Extract initial words from a String, or take the entire
     * <code>value</code> if not enough words can be identified. (If
     * <code>n</code> is not 1 or more, returns an empty String.) (A "word"
     * ends at each and every space character.)
     *
     * @param value The source from which words are cut
     * @param n     The number of words to try to extract
     * @return The extracted words or <code>""</code>
     */
    private static String getWords(String value, int n) {
        if (n < 1) {
            return "";
        }
        int l = 0;
        while (n-- > 0) {
            int o = l > 0 ? l + 1 : l;
            int i = value.indexOf(' ', o);
            if (i == -1) {
                return value;
            }
            l = i;
        }
        return value.substring(0, l);
    }

    /**
     * Extract an opening string from the input stream, past any BOM, and past
     * any initial whitespace, but only up to <code>OPENING_MAX_CHARS</code> or
     * to the first <code>\n</code> after any non-whitespace. (Hashbang, #!,
     * openings will have superfluous space removed.)
     *
     * @param in  The input stream containing the data
     * @param sig The initial sequence of bytes in the input stream
     * @return The extracted string or <code>""</code>
     * @throws IOException in case of any read error
     */
    private static String readOpening(InputStream in, byte[] sig)
            throws IOException {

        in.mark(MARK_READ_LIMIT);

        String encoding = IOUtils.findBOMEncoding(sig);
        if (encoding == null) {
            // SRCROOT is read with UTF-8 as a default.
            encoding = StandardCharsets.UTF_8.name();
        } else {
            int skipForBOM = IOUtils.skipForBOM(sig);
            if (in.skip(skipForBOM) < skipForBOM) {
                in.reset();
                return "";
            }
        }

        int nRead = 0;
        boolean sawNonWhitespace = false;
        boolean lastWhitespace = false;
        boolean postHashbang = false;
        int r;

        StringBuilder opening = new StringBuilder();
        BufferedReader readr = new BufferedReader(
                new InputStreamReader(in, encoding), OPENING_MAX_CHARS);
        while ((r = readr.read()) != -1) {
            if (++nRead > OPENING_MAX_CHARS) {
                break;
            }
            char c = (char) r;
            boolean isWhitespace = Character.isWhitespace(c);
            if (!sawNonWhitespace) {
                if (isWhitespace) {
                    continue;
                }
                sawNonWhitespace = true;
            }
            if (c == '\n') {
                break;
            }

            if (isWhitespace) {
                // Track `lastWhitespace' to condense stretches of whitespace,
                // and use ' ' regardless of actual whitespace character to
                // accord with magic string definitions.
                if (!lastWhitespace && !postHashbang) {
                    opening.append(' ');
                }
            } else {
                opening.append(c);
                postHashbang = false;
            }
            lastWhitespace = isWhitespace;

            // If the opening starts with "#!", then track so that any
            // trailing whitespace after the hashbang is ignored.
            if (opening.length() == 2) {
                if (opening.charAt(0) == '#' && opening.charAt(1) == '!') {
                    postHashbang = true;
                }
            }
        }

        in.reset();
        return opening.toString();
    }

    /**
     * Get the default Analyzer.
     *
     * @return default FileAnalyzer
     */
    public static AbstractAnalyzer getAnalyzer() {
        return AnalyzerFramework.DEFAULT_ANALYZER_FACTORY.getAnalyzer();
    }

    /**
     * Gets an analyzer for the specified {@code fileTypeName} if it accords
     * with a known {@link FileAnalyzer#getFileTypeName()}.
     *
     * @param fileTypeName a defined name
     * @return a defined instance if known or otherwise {@code null}
     */
    public AbstractAnalyzer getAnalyzer(String fileTypeName) {
        AnalyzerFactory factory = findByFileTypeName(fileTypeName);
        return factory == null ? null : factory.getAnalyzer();
    }

    /**
     * Get an analyzer suited to analyze a file. This function will reuse
     * analyzers since they are costly.
     *
     * @param in   Input stream containing data to be analyzed
     * @param file Name of the file to be analyzed
     * @return An analyzer suited for that file content
     * @throws java.io.IOException If an error occurs while accessing the data
     *                             in the input stream.
     */
    public AbstractAnalyzer getAnalyzer(InputStream in, String file) throws IOException {
        AnalyzerFactory factory = find(in, file);
        if (factory == null) {
            AbstractAnalyzer defaultAnalyzer = getAnalyzer();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "{0}: fallback {1}",
                        new Object[]{file,
                                defaultAnalyzer.getClass().getSimpleName()});
            }
            return defaultAnalyzer;
        }
        return factory.getAnalyzer();
    }

    /**
     * Free resources associated with all registered analyzers.
     */
    public void returnAnalyzers() {
        pluginFramework.returnAnalyzers();
    }

    /**
     * Populate a Lucene document with the required fields.
     *
     * @param doc     The document to populate
     * @param file    The file to index
     * @param path    Where the file is located (from source root)
     * @param fa      The analyzer to use on the file
     * @param xrefOut Where to write the xref (possibly {@code null})
     * @throws IOException               If an exception occurs while collecting the data
     * @throws InterruptedException      if a timeout occurs
     * @throws ForbiddenSymlinkException if symbolic-link checking encounters
     *                                   an ineligible link
     */
    public void populateDocument(Document doc, File file, String path,
            AbstractAnalyzer fa, Writer xrefOut) throws IOException,
            InterruptedException, ForbiddenSymlinkException {

        String date = DateTools.timeToString(file.lastModified(),
                DateTools.Resolution.MILLISECOND);
        path = Util.fixPathIfWindows(path);
        doc.add(new Field(QueryBuilder.U, Util.path2uid(path, date),
                string_ft_stored_nanalyzed_norms));
        doc.add(new Field(QueryBuilder.FULLPATH, file.getAbsolutePath(),
                string_ft_nstored_nanalyzed_norms));
        doc.add(new SortedDocValuesField(QueryBuilder.FULLPATH,
                new BytesRef(file.getAbsolutePath())));

        if (RuntimeEnvironment.getInstance().isHistoryEnabled()) {
            try {
                HistoryReader hr = HistoryGuru.getInstance().getHistoryReader(file);
                if (hr != null) {
                    doc.add(new TextField(QueryBuilder.HIST, hr));
                    // date = hr.getLastCommentDate() //RFE
                }
            } catch (HistoryException e) {
                LOGGER.log(Level.WARNING, "An error occurred while reading history: ", e);
            }
        }
        doc.add(new Field(QueryBuilder.DATE, date, string_ft_stored_nanalyzed_norms));
        doc.add(new SortedDocValuesField(QueryBuilder.DATE, new BytesRef(date)));

        // `path' is not null, as it was passed to Util.path2uid() above.
        doc.add(new TextField(QueryBuilder.PATH, path, Store.YES));
        Project project = Project.getProject(path);
        if (project != null) {
            doc.add(new TextField(QueryBuilder.PROJECT, project.getPath(), Store.YES));
        }

        /*
         * Use the parent of the path -- not the absolute file as is done for
         * FULLPATH -- so that DIRPATH is the same convention as for PATH
         * above. A StringField, however, is used instead of a TextField.
         */
        File fpath = new File(path);
        String fileParent = fpath.getParent();
        if (fileParent != null && fileParent.length() > 0) {
            String normalizedPath = QueryBuilder.normalizeDirPath(fileParent);
            StringField npstring = new StringField(QueryBuilder.DIRPATH,
                    normalizedPath, Store.NO);
            doc.add(npstring);
        }

        if (fa != null) {
            AbstractAnalyzer.Genre g = fa.getGenre();
            if (g == AbstractAnalyzer.Genre.PLAIN || g == AbstractAnalyzer.Genre.XREFABLE || g == AbstractAnalyzer.Genre.HTML) {
                doc.add(new Field(QueryBuilder.T, g.typeName(), string_ft_stored_nanalyzed_norms));
            }
            fa.analyze(doc, StreamSource.fromFile(file), xrefOut);

            String type = fa.getFileTypeName();
            doc.add(new StringField(QueryBuilder.TYPE, type, Store.YES));
        }
    }

    /**
     * Get the content type for a named file.
     *
     * @param in   The input stream we want to get the content type for (if we
     *             cannot determine the content type by the filename)
     * @param file The name of the file
     * @return The contentType suitable for printing to
     * response.setContentType() or null if the factory was not found
     * @throws java.io.IOException If an error occurs while accessing the input
     *                             stream.
     */
    public String getContentType(InputStream in, String file) throws IOException {
        AnalyzerFactory factory = find(in, file);
        String type = null;
        if (factory != null) {
            type = factory.getContentType();
        }
        return type;
    }

    /**
     * Write a browse-able version of the file
     *
     * @param factory    The analyzer factory for this file type
     * @param in         The input stream containing the data
     * @param out        Where to write the result
     * @param defs       definitions for the source file, if available
     * @param annotation Annotation information for the file
     * @param project    Project the file belongs to
     * @throws java.io.IOException If an error occurs while creating the output
     */
    public static void writeXref(AnalyzerFactory factory, Reader in,
            Writer out, Definitions defs,
            Annotation annotation, Project project)
            throws IOException {
        Reader input = in;
        if (factory.getGenre() == AbstractAnalyzer.Genre.PLAIN) {
            // This is some kind of text file, so we need to expand tabs to
            // spaces to match the project's tab settings.
            input = ExpandTabsReader.wrap(in, project);
        }

        WriteXrefArgs args = new WriteXrefArgs(input, out);
        args.setDefs(defs);
        args.setAnnotation(annotation);
        args.setProject(project);

        AbstractAnalyzer analyzer = factory.getAnalyzer();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        analyzer.setScopesEnabled(env.isScopesEnabled());
        analyzer.setFoldingEnabled(env.isFoldingEnabled());
        analyzer.writeXref(args);
    }

    /**
     * Writes a browse-able version of the file transformed for immediate
     * serving to a web client.
     *
     * @param contextPath the web context path for
     *                    {@link Util#dumpXref(java.io.Writer, java.io.Reader, java.lang.String)}
     * @param factory     the analyzer factory for this file type
     * @param in          the input stream containing the data
     * @param out         a defined instance to write
     * @param defs        definitions for the source file, if available
     * @param annotation  annotation information for the file
     * @param project     project the file belongs to
     * @throws java.io.IOException if an error occurs while creating the output
     */
    public static void writeDumpedXref(String contextPath,
            AnalyzerFactory factory, Reader in, Writer out,
            Definitions defs, Annotation annotation, Project project)
            throws IOException {

        File xrefTemp = File.createTempFile("ogxref", ".html");
        try {
            try (FileWriter tmpout = new FileWriter(xrefTemp)) {
                writeXref(factory, in, tmpout, defs, annotation, project);
            }
            Util.dumpXref(out, xrefTemp, false, contextPath);
        } finally {
            xrefTemp.delete();
        }
    }

    /**
     * Register the given prefix for the analyzer factory.
     *
     * @param prefix  the prefix
     * @param factory the factory
     * @see AnalyzerFramework#addPrefix(String, AnalyzerFactory)
     */
    public void addPrefix(String prefix, AnalyzerFactory factory) {
        this.pluginFramework.addPrefix(prefix, factory);
    }

    /**
     * Register the given extension for the analyzer factory.
     *
     * @param extension the extension
     * @param factory   the factory
     * @see AnalyzerFramework#addExtension(String, AnalyzerFactory) (String, AnalyzerFactory)
     */
    public void addExtension(String extension, AnalyzerFactory factory) {
        this.pluginFramework.addExtension(extension, factory);
    }

    /**
     * Return the instance of analyzer plugin framework.
     *
     * @return the instance of analyzer plugin framework
     */
    public AnalyzerFramework getPluginFramework() {
        return pluginFramework;
    }
}
