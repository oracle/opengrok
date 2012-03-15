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
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.IOUtils;

/**
 * Base class for all different File Analyzers
 *
 * An Analyzer for a filetype provides
 *<ol>
 * <li>the file extentions and magic numbers it analyzes</li>
 * <li>a lucene document listing the fields it can support</li>
 * <li>TokenStreams for each of the field it said requires tokenizing in 2</li>
 * <li>cross reference in HTML format</li>
 * <li>The type of file data, plain text etc</li>
 *</ol>
 *
 * Created on September 21, 2005
 *
 * @author Chandan
 */
public class FileAnalyzer extends Analyzer {

    protected Project project;
    private final FileAnalyzerFactory factory;

    /**
     * What kind of file is this?
     */
    public static enum Genre {
        /** xrefed - line numbered context */
        PLAIN("p"),
        /** xrefed - summarizer context */
        XREFABLE("x"),
        /** not xrefed - no context - used by diff/list */
        IMAGE("i"),
        /** not xrefed - no context */
        DATA("d"),
        /** not xrefed - summarizer context from original file */
        HTML("h")
        ;
        private String typeName;
        private Genre(String typename) {
            this.typeName = typename;
        }

        /**
         * Get the type name value used to tag lucence documents.
         * @return a none-null string.
         */
        public String typeName() {
            return typeName;
        }

        /**
         * Get the Genre for the given type name.
         * @param typeName name to check
         * @return {@code null} if it doesn't match any genre, the genre otherwise.
         * @see #typeName()
         */
        public static Genre get(String typeName) {
            if (typeName == null) {
                return null;
            }
            for (Genre g : values()) {
                if (g.typeName.equals(typeName)) {
                    return g;
                }
            }
            return null;
        }
    }
    protected Ctags ctags;

    public void setCtags(Ctags ctags) {
        this.ctags = ctags;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    /**
     * Get the factory which created this analyzer.
     * @return the {@code FileAnalyzerFactory} which created this analyzer
     */
    public final FileAnalyzerFactory getFactory() {
        return factory;
    }

    public Genre getGenre() {
        return factory.getGenre();
    }
    private final HistoryAnalyzer hista;

    /** Creates a new instance of FileAnalyzer */
    public FileAnalyzer(FileAnalyzerFactory factory) {
        this.factory = factory;
        hista = new HistoryAnalyzer();
    }

    public void analyze(Document doc, InputStream in) throws IOException {
        // not used
    }
        
    public TokenStream overridableTokenStream(String fieldName, Reader reader) {
        if ("path".equals(fieldName) || "project".equals(fieldName)) {
            return new PathTokenizer(reader);
        } else if ("hist".equals(fieldName)) {
            return hista.tokenStream(fieldName, reader);
        }
        OpenGrokLogger.getLogger().log(Level.WARNING, "Have no analyzer for: {0}", fieldName);
        return null;
    }

    @Override
    public final TokenStream tokenStream(String fieldName, Reader reader) {
        return this.overridableTokenStream(fieldName, reader);
    }        
        
    @Override    
    public final TokenStream reusableTokenStream(String fieldName, Reader reader) {
        //TODO needs refactoring to get more speed and less ram usage for indexer
        return this.tokenStream(fieldName, reader);
    }           

    /**
     * Write a cross referenced HTML file.
     * @param out to writer HTML cross-reference
     * @throws java.io.IOException if an error occurs
     */
    public void writeXref(Writer out) throws IOException {
        out.write("Error General File X-Ref writer!");
    }

    public void writeXref(File xrefDir, String path) throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        final boolean compressed = env.isCompressXref();
        final File file = new File(xrefDir, path + (compressed ? ".gz" : ""));
        OutputStream out = new FileOutputStream(file);
        try {
            if (compressed) {
                out = new GZIPOutputStream(out);
            }
            Writer w = new BufferedWriter(new OutputStreamWriter(out));
            writeXref(w);
            IOUtils.close(w);
        } finally {
            IOUtils.close(out);
        }
    }
}
