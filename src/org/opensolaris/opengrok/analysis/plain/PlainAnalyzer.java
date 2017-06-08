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
 */
package org.opensolaris.opengrok.analysis.plain;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.ExpandTabsReader;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.IteratorReader;
import org.opensolaris.opengrok.analysis.JFlexXref;
import org.opensolaris.opengrok.analysis.Scopes;
import org.opensolaris.opengrok.analysis.StreamSource;
import org.opensolaris.opengrok.analysis.TextAnalyzer;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.util.NullWriter;

/**
 * Analyzer for plain text files Created on September 21, 2005
 *
 * @author Chandan
 */
public class PlainAnalyzer extends TextAnalyzer {

    private JFlexXref xref;
    private Definitions defs;

    /**
     * Creates a new instance of PlainAnalyzer
     * @param factory name of factory
     */
    protected PlainAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
    }

    /**
     * Create an xref for the language supported by this analyzer.
     * @param reader the data to produce xref for
     * @return an xref instance
     */
    protected JFlexXref newXref(Reader reader) {
        return new PlainXref(reader);
    }

    @Override
    protected Reader getReader(InputStream stream) throws IOException {
        return ExpandTabsReader.wrap(super.getReader(stream), project);
    }
    
    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut) throws IOException {
        doc.add(new TextField(QueryBuilder.FULL, getReader(src.getStream())));
        String fullpath = doc.get(QueryBuilder.FULLPATH);
        if (fullpath != null && ctags != null) {
            defs = ctags.doCtags(fullpath + "\n");
            if (defs != null && defs.numberOfSymbols() > 0) {
                doc.add(new TextField(QueryBuilder.DEFS, new IteratorReader(defs.getSymbols())));
                //this is to explicitly use appropriate analyzers tokenstream to workaround #1376 symbols search works like full text search 
                TextField ref=new TextField(QueryBuilder.REFS,this.SymbolTokenizer);
                this.SymbolTokenizer.setReader(getReader(src.getStream()));
                doc.add(ref);
                byte[] tags = defs.serialize();
                doc.add(new StoredField(QueryBuilder.TAGS, tags));                
            }
        }
        
        if (scopesEnabled && xrefOut == null) {
            /*
             * Scopes are generated during xref generation. If xrefs are
             * turned off we still need to run writeXref to produce scopes,
             * we use a dummy writer that will throw away any xref output.
             */
            xrefOut = new NullWriter();
        }

        if (xrefOut != null) {
            try (Reader in = getReader(src.getStream())) {
                writeXref(in, xrefOut);
            }
            
            Scopes scopes = xref.getScopes();
            if (scopes.size() > 0) {
                byte[] scopesSerialized = scopes.serialize();
                doc.add(new StoredField(QueryBuilder.SCOPES, scopesSerialized));
            }
        }
    }

    /**
     * Write a cross referenced HTML file.
     *
     * @param in Input source
     * @param out Writer to write HTML cross-reference
     */
    private void writeXref(Reader in, Writer out) throws IOException {
        if (xref == null) {
            xref = newXref(in);
        } else {
            xref.reInit(in);
        }
        xref.setDefs(defs);
        xref.setScopesEnabled(scopesEnabled);
        xref.setFoldingEnabled(foldingEnabled);
        xref.project = project;
        xref.write(out);
    }

    /**
     * Write a cross referenced HTML file reads the source from in
     *
     * @param in Input source
     * @param out Output xref writer
     * @param defs definitions for the file (could be null)
     * @param annotation annotation for the file (could be null)
     */
    static void writeXref(Reader in, Writer out, Definitions defs, Annotation annotation, Project project) throws IOException {
        PlainXref xref = new PlainXref(in);
        xref.annotation = annotation;
        xref.project = project;
        xref.setDefs(defs);
        xref.write(out);
    }
}
