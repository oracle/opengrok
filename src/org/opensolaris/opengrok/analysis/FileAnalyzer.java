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
 * ident	"@(#)FileAnalyzer.java 1.2     05/12/01 SMI"
 */
package org.opensolaris.opengrok.analysis;

import java.io.*;
import org.apache.lucene.document.*;
import org.apache.lucene.analysis.*;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.*;

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
    public static String[] magics = null;
    public static String[] suffixe = null;
    public static String contentType = null;
    
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
    public static Genre g = Genre.DATA;
    
    public Genre getGenre() {
	return this.g;
    }
    
    private HistoryAnalyzer hista;
    /** Creates a new instance of FileAnalyzer */
    public FileAnalyzer() {
	hista = new HistoryAnalyzer();
    }
    
    public void analyze(Document doc, InputStream in) {
    }
    
    public TokenStream tokenStream(String fieldName, Reader reader) {
	if ("path".equals(fieldName) || "project".equals(fieldName)) {
	    return new PathTokenizer(reader);
	} else if("hist".equals(fieldName)) {
	    return hista.tokenStream(fieldName, reader);
        }
        
        if (RuntimeEnvironment.getInstance().isVerbose()) {
            System.out.println("Have no analyzer for: " + fieldName);
        }
	return null;
    }
    
    /**
     * Write a cross referenced HTML file.
     * @param out to writer HTML cross-reference
     */
    public void writeXref(Writer out) throws IOException {
	out.write("Error General File X-Ref writer!");
    }
    
    /**
     * Write a cross referenced HTML file.
     * @param xrefdir path of file root xref directory
     * @param path path from xrefdir
     */
    public void writeXref(String xrefdir, String path) throws IOException {
	//  System.err.println("parent " + xrefdir + " child " + path);
	Writer out = new BufferedWriter(new FileWriter(new File(xrefdir+ File.separatorChar +path)));
	writeXref(out);
	out.close();
    }

    /**
     * Write a cross referenced HTML file reads the source from in
     * @param in Input source
     * @param out Output xref writer
     * @param annotation annotation for the file (could be null)
     */
    public static void writeXref(InputStream in, Writer out,
                                 Annotation annotation) throws IOException {
	throw new UnsupportedOperationException("Not yet implemented");
    }
    
    public void writeXref(File xrefDir, String path) throws IOException {
	Writer out = new BufferedWriter(new FileWriter(new File(xrefDir, path)));
	writeXref(out);
	out.close();
    }
    
    public static char[] readContent(char[] content, InputStream in, Integer length) throws IOException {
	InputStreamReader inReader = new InputStreamReader(in);
	int len = 0;
	do{
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
	length = len;
	return content;
    }
}
