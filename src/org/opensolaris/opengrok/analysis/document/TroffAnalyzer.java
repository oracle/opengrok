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
package org.opensolaris.opengrok.analysis.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;

/**
 * Analyzes [tn]roff files
 * Created on September 30, 2005
 *
 * @author Chandan
 */
public class TroffAnalyzer extends FileAnalyzer {
    private char[] content;
    private int len;
    
    private final TroffFullTokenizer troffull;
    private final TroffXref xref;
    Reader dummy = new StringReader("");
    /**
     * Creates a new instance of TroffAnalyzer
     */
    protected TroffAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
	troffull = new TroffFullTokenizer(dummy);
	xref = new TroffXref(dummy);
	content = new char[12*1024];
    }
    
    public void analyze(Document doc, InputStream in) {
	try {
	    len = 0;
	    do{
		InputStreamReader inReader = new InputStreamReader(in);
		int rbytes = inReader.read(content, len, content.length - len);
		if(rbytes > 0 ) {
		    if(rbytes == (content.length - len)) {
			char[] content2 = new char[content.length * 2];
			System.arraycopy(content,0, content2, 0, content.length);
			content = content2;
		    }
		    len += rbytes;
		} else {
		    break;
		}
	    } while(true);
	} catch (IOException e) {
	    return;
	}
	doc.add(new Field("full", new StringReader("")));
    }
    
    public TokenStream tokenStream(String fieldName, Reader reader) {
	if("full".equals(fieldName)) {
	    troffull.reInit(content, len);
	    return troffull;
	}
	return super.tokenStream(fieldName, reader);
    }
    
    /**
     * Write a cross referenced HTML file.
     * @param out Writer to write HTML cross-reference
     */
    public void writeXref(Writer out) throws IOException {
	xref.reInit(content, len);
        xref.project = project;
	out.write("</pre>");
	xref.write(out);
	out.write("<pre>");
    }
    
    /**
     * Write a cross referenced HTML file reads the source from in
     * @param in Input source
     * @param out Output xref writer
     * @param annotation annotation for the file (could be null)
     */
    static void writeXref(InputStream in, Writer out, Annotation annotation, Project project) throws IOException {
	TroffXref xref = new TroffXref(in);
        xref.project = project;
	out.write("</pre>");
	xref.write(out);
	out.write("<pre>");
    }
}
