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
import java.util.zip.GZIPOutputStream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

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
	PLAIN,   // xrefed - line numbered context
	XREFABLE,   // xrefed - summarizer context
	IMAGE,   // not xrefed - no context - used by diff/list
	DATA,   // not xrefed - no context
	HTML    // not xrefed - summarizer context from original file
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
    
    public void analyze(Document doc, InputStream in) {
        // not used
    }
    
    public TokenStream tokenStream(String fieldName, Reader reader) {
	if ("path".equals(fieldName) || "project".equals(fieldName)) {
	    return new PathTokenizer(reader);
	} else if("hist".equals(fieldName)) {
	    return hista.tokenStream(fieldName, reader);
        }
        
        if (RuntimeEnvironment.getInstance().isVerbose()) {
            OpenGrokLogger.getLogger().info("Have no analyzer for: " + fieldName);
        }
	return null;
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

        if (env.hasProjects()) {
            project = Project.getProject(path);
        } else {
            project = null;
        }

        final boolean compressed = env.isCompressXref();
        final File file = new File(xrefDir, path + (compressed ? ".gz" : ""));
        OutputStream out = new FileOutputStream(file);
        try {
            if (compressed) {
                out = new GZIPOutputStream(out);
            }
            Writer w = new BufferedWriter(new OutputStreamWriter(out));
            writeXref(w);
            w.close();
        } finally {
            out.close();
        }
    }
}
