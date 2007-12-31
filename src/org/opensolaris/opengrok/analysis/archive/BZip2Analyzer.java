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

package org.opensolaris.opengrok.analysis.archive;

import org.apache.lucene.analysis.*;
import java.io.*;
import org.opensolaris.opengrok.analysis.*;
import org.opensolaris.opengrok.analysis.plain.*;
import org.apache.lucene.document.*;
import org.apache.tools.bzip2.*;

/**
 * Analyzes a BZip2 file
 * Created on September 22, 2005
 *
 * @author Chandan
 */

public class BZip2Analyzer extends FileAnalyzer {
    private Genre g;
    
    public Genre getGenre() {
	if (g != null) {
            return g;
        }
        return super.getGenre();
    }

    protected BZip2Analyzer(FileAnalyzerFactory factory) {
	super(factory);
    }
    
    private FileAnalyzer fa;
    public void analyze(Document doc, InputStream in) {
	try {
	    if (in.read() != 'B') { throw new IOException("Not BZIP2 format"); };
	    if (in.read() != 'Z') { throw new IOException("Not BZIP2 format"); };
	    BufferedInputStream gzis = new BufferedInputStream(new CBZip2InputStream(in));
	    String path = doc.get("path");
	    if(path != null && 
		(path.endsWith(".bz2") || path.endsWith(".BZ2") || path.endsWith(".bz"))
		) {
		String newname = path.substring(0, path.lastIndexOf('.'));
		//System.err.println("BZIPPED OF = " + newname);
		fa = AnalyzerGuru.getAnalyzer(gzis, newname);
		if(fa != null && fa.getClass() != BZip2Analyzer.class) {
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
		} else {
		    fa = null;
		    //System.err.println("Did not analyze " + newname);
		}
	    }
	} catch (Exception e) {
	    System.err.println("BZIP2 Analyzer " + e);
	}
    }
    
    public TokenStream tokenStream(String fieldName, Reader reader) {
	if(fa != null) {
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
