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
 * ident	"%Z%%M% %I%     %E% SMI"
 */
package org.opensolaris.opengrok.analysis.plain;
import java.io.*;
import org.opensolaris.opengrok.analysis.*;
import org.apache.lucene.document.*;
import org.apache.lucene.analysis.*;
import org.opensolaris.opengrok.history.Annotation;

/**
 * Analyzes HTML files
 * Created on September 30, 2005
 *
 * @author Chandan
 */
public class XMLAnalyzer extends FileAnalyzer {
    
    private char[] content;
    private int len;
    private PlainFullTokenizer plainfull;
    private XMLXref xref;
    private static final Reader dummy = new StringReader("");
    
    /**
     * Creates a new instance of XMLAnalyzer
     */
    protected XMLAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
	content = new char[64 * 1024];
	len = 0;
	plainfull = new PlainFullTokenizer(dummy);
	xref = new XMLXref(dummy);
    }
    
    public void analyze(Document doc, InputStream in) {
	try {
	    InputStreamReader inReader = new InputStreamReader(in);
	    len = 0;
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
	} catch (IOException e) {
	    return;
	}
	doc.add(new Field("full", dummy));
    }
    
    public TokenStream tokenStream(String fieldName, Reader reader) {
	if ("full".equals(fieldName)) {
	    plainfull.reInit(content, len);
	    return plainfull;
	}
	return super.tokenStream(fieldName, reader);
    }
    
    /**
     * Write a cross referenced HTML file.
     * @param out Writer to write HTML cross-reference
     */
    public void writeXref(Writer out) throws IOException {
	xref.reInit(content, len);
	xref.write(out);
    }
    
    /**
     * Write a cross referenced HTML file reads the source from in
     * @param in Input source
     * @param out Output xref writer
     * @param annotation annotation for the file (could be null)
     */
    static void writeXref(InputStream in, Writer out,
                                 Annotation annotation) throws IOException {
	XMLXref xref = new XMLXref(in);
        xref.annotation = annotation;
	xref.write(out);
    }
}
