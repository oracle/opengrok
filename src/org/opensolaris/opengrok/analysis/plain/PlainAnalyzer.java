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

import org.apache.lucene.document.*;
import org.apache.lucene.analysis.*;
import java.io.*;
import org.opensolaris.opengrok.analysis.*;
import java.util.*;
import java.util.prefs.*;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.Annotation;

/**
 * Analyzer for plain text files
 * Created on September 21, 2005
 *
 * @author Chandan
 */
public class PlainAnalyzer extends FileAnalyzer {
    protected char[] content;
    protected int len;
    private PlainFullTokenizer plainfull;
    private PlainSymbolTokenizer plainref;
    private PlainXref xref;
    private static final Reader dummy = new StringReader(" ");
    private Ctags ctags;
    protected HashMap<String, HashMap<Integer, String>> defs;
    
    /** Creates a new instance of PlainAnalyzer */
    protected PlainAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
        content = new char[64 * 1024];
        len = 0;
        plainfull = new PlainFullTokenizer(dummy);
        plainref = new PlainSymbolTokenizer(dummy);
        xref = new PlainXref((Reader) null);
        try {
            ctags = new Ctags();
        } catch (IOException e) {
        }
        if(ctags == null) {
            System.err.println("WARNING: unable to run ctags! searching definitions will not work!");
        }
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
        try {
	    String fullpath;
	    if((fullpath = doc.get("fullpath")) != null && ctags != null) {
                defs = ctags.doCtags(fullpath+"\n");
                if(defs != null && defs.size() > 0) {
                    doc.add(new Field("defs", dummy));
                    doc.add(new Field("refs", dummy)); //XXX adding a refs field only if it has defs?
                    doc.add(new Field("tags", ctags.tagString(), Field.Store.YES, Field.Index.UN_TOKENIZED));
                }
            }
        } catch (IOException e) {
        }
    }    
    
    public TokenStream tokenStream(String fieldName, Reader reader) {
        if("full".equals(fieldName)) {
            plainfull.reInit(content, len);
            return plainfull;
        } else if ("refs".equals(fieldName)) {
            plainref.reInit(content, len);
            return plainref;
        } else if("defs".equals(fieldName)) {
            return new Hash2TokenStream(defs);
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
        xref.write(out);
    }

    /**
     * Write a cross referenced HTML file reads the source from in
     * @param in Input source
     * @param out Output xref writer
     * @param annotation annotation for the file (could be null)
     */
    static void writeXref(InputStream in, Writer out, Annotation annotation, Project project) throws IOException {
        PlainXref xref = new PlainXref(in);
        xref.annotation = annotation;
        xref.project = project;
        xref.write(out);
    }
}
