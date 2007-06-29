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
 * ident	"@(#)GZIPAnalyzer.java 1.1     05/11/11 SMI"
 */

package org.opensolaris.opengrok.analysis.archive;

import org.apache.lucene.analysis.*;
import java.io.*;
import org.opensolaris.opengrok.analysis.*;
import org.apache.lucene.document.*;
import java.util.zip.*;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;

/**
 * Analyzes GZip files
 * Created on September 22, 2005
 *
 * @author Chandan
 */

public class GZIPAnalyzer extends FileAnalyzer {
    /** Creates a new instance of ZipAnalyzer */
    public static String[] suffixes = {
	"GZ"
    };
    public static String[] magics = {
	"\037\213"
    };
    public static Genre g;
    public Genre getGenre() {
	return this.g;
    }
    public GZIPAnalyzer() {
	super();
    }
    
    private FileAnalyzer fa;
    
    public void analyze(Document doc, InputStream in) {
	try {
	    BufferedInputStream gzis = new BufferedInputStream(new GZIPInputStream(in));
	    String path = doc.get("path");
	    if(path != null && 
		(path.endsWith(".gz") || path.endsWith(".GZ") || path.endsWith(".Gz"))) {
		String newname = path.substring(0, path.length() - 3);
		//System.err.println("GZIPPED OF = " + newname);
		fa = AnalyzerGuru.getAnalyzer(gzis, newname);
		if(fa != null) { // cant recurse!
		    if(fa.getGenre() == Genre.PLAIN || fa.getGenre() == Genre.XREFABLE) {
			this.g = Genre.XREFABLE;
		    } else {
			this.g = Genre.DATA;
		    }
		    fa.analyze(doc, gzis);
		    if(doc.get("t") != null) {
			doc.removeField("t");
			if (g == Genre.XREFABLE) {
			    doc.add(new Field("t", "x", Field.Store.YES, Field.Index.UN_TOKENIZED));
			}
		    }
		    return;
		} else {
		    fa = null;
		    this.g = Genre.DATA;
		    System.err.println("Did not analyze " + newname);
		}
	    }
	} catch (IOException e) {
	    System.err.println("GZIP Analyzer " + e);
	}
    }
    
    public TokenStream tokenStream(String fieldName, Reader reader) {
	if (fa != null) {
	    return fa.tokenStream(fieldName, reader);
	}
	return super.tokenStream(fieldName, reader);
    }
    
    /**
     * Write a cross referenced HTML file.
     * @param out Writer to store HTML cross-reference
     */
    public void writeXref(Writer out) throws IOException {
	if(fa != null) {
	    if(fa.getGenre() == Genre.PLAIN || fa.getGenre() == Genre.XREFABLE) {
		fa.writeXref(out);
	    }
	}
    }
}
