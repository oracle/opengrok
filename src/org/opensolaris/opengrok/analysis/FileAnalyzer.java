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
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
 * Use is subject to license terms.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.opensolaris.opengrok.analysis.plain.PlainFullTokenizer;
import org.opensolaris.opengrok.analysis.plain.PlainSymbolTokenizer;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.logger.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(FileAnalyzer.class);

    protected Project project;
    protected boolean scopesEnabled;
    protected boolean foldingEnabled;
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
        private final String typeName;
        private Genre(String typename) {
            this.typeName = typename;
        }

        /**
         * Get the type name value used to tag lucene documents.
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
    
    public void setScopesEnabled(boolean scopesEnabled) {
        this.scopesEnabled = supportsScopes() && scopesEnabled;
    }
    
    public void setFoldingEnabled(boolean foldingEnabled) {
        this.foldingEnabled = supportsScopes() && foldingEnabled;
    }
    
    protected boolean supportsScopes() {
        return false;
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

    public static Reader dummyReader=new StringReader("");
    
    /** Creates a new instance of FileAnalyzer
     * @param factory name of factory to be used 
     */
    public FileAnalyzer(FileAnalyzerFactory factory) {
        super(Analyzer.PER_FIELD_REUSE_STRATEGY);
        this.factory = factory;        
        SymbolTokenizer=new PlainSymbolTokenizer(dummyReader);
    }
    
    /**
     * Returns the normalized name of the analyzer,
     * which should corresponds to the file type.
     * Example: The analyzer for the C language (CAnalyzer) would return “c”.
     * @return Normalized name of the analyzer.
     */
    public String getFileTypeName() {
        String name = this.getClass().getSimpleName().toLowerCase();
        String suffix = "analyzer";
        
        if (name.endsWith(suffix)) {
            return name.substring(0, name.length() - suffix.length());
        }
        
        return name.toLowerCase();
    }

    /**
     * Analyze the contents of a source file. This includes populating the
     * Lucene document with fields to add to the index, and writing the
     * cross-referenced data to the specified destination.
     * @param doc the Lucene document
     * @param src the input data source
     * @param xrefOut where to write the xref (may be {@code null})
     * @throws IOException if any I/O error
     */
    public void analyze(Document doc, StreamSource src, Writer xrefOut) throws IOException {
        // not used
    }

    /**
     * Derived classes should override to write a cross referenced HTML file
     * for the specified args.
     * @param args a defined instance
     * @return the instance used to write the cross-referencing
     * @throws java.io.IOException if an error occurs
     */
    public JFlexXref writeXref(WriteXrefArgs args) throws IOException {
        throw new UnsupportedOperationException(
            "Base FileAnalyzer cannot write xref");
    }
    
    // you analyzer HAS to override this to get proper symbols in results
    protected JFlexTokenizer SymbolTokenizer;
    
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        switch (fieldName) {
            case "full":
                return new TokenStreamComponents(new PlainFullTokenizer(dummyReader));
            case "path":
            case "project":
                return new TokenStreamComponents(new PathTokenizer());
            case "hist":
                return new HistoryAnalyzer().createComponents(fieldName);
                //below is set by PlainAnalyzer to workaround #1376 symbols search works like full text search 
            case "refs": {                
                    return new TokenStreamComponents(SymbolTokenizer);
            }
            case "defs":
                return new TokenStreamComponents(new PlainSymbolTokenizer(dummyReader));
            default:
                LOGGER.log(
                        Level.WARNING, "Have no analyzer for: {0}", fieldName);
                return null;
        }
    }
}
