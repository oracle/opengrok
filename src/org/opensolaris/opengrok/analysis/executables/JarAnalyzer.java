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
package org.opensolaris.opengrok.analysis.executables;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.List2TokenStream;
import org.opensolaris.opengrok.analysis.TagFilter;
import org.opensolaris.opengrok.analysis.plain.PlainFullTokenizer;

/**
 * Analyzes JAR, WAR, EAR (Java Archive) files.
 * Created on September 22, 2005
 *
 * @author Chandan
 */

public class JarAnalyzer extends FileAnalyzer {
    private byte[] content;

    private List<String> defs;
    private List<String> refs;
    private StringWriter xref;

    private static final Reader dummy = new StringReader("");
    protected JarAnalyzer(FileAnalyzerFactory factory) {
	super(factory);
	content = new byte[16*1024];
    }
    
    public void analyze(Document doc, InputStream in) {
	defs = new LinkedList<String>();
	refs = new LinkedList<String>();
	StringBuilder fullText = new StringBuilder();
	xref = new StringWriter();
	try {
	    ZipInputStream zis = new ZipInputStream(in);
	    ZipEntry entry;
	    byte buf[] = new byte[1024];
	    while ((entry = zis.getNextEntry()) != null) {
		String ename = entry.getName();
		xref.write("<br/><b>"+ ename + "</b>");
		fullText.append(ename);
		fullText.append('\n');
		int len = 0;
                FileAnalyzerFactory fac = AnalyzerGuru.find(ename);
		if (fac instanceof JavaClassAnalyzerFactory) {
                    JavaClassAnalyzer jca =
                        (JavaClassAnalyzer) fac.getAnalyzer();
		    BufferedInputStream bif = new BufferedInputStream(zis);
		    int r;
		    while((r = bif.read(buf)) > 0) {
			if( len + r > content.length) {
			    byte[] content2 = new byte[content.length*2];
			    System.arraycopy(content, 0, content2, 0, len);
			    content = content2;
			}
			System.arraycopy(buf, 0, content, len, r);
			len += r;
		    }
		    jca.analyze(doc, new ByteArrayInputStream(content));
		    doc.removeField("defs");
		    doc.removeField("refs");
    		    doc.removeField("full");
		    defs.addAll(jca.getDefs());
		    refs.addAll(jca.getRefs());
		    fullText.append(jca.getFull());
		    xref.write("<pre>");
		    jca.writeXref(xref);
		    xref.write("</pre>");
		}
	    }
	    doc.add(new Field("full", new TagFilter(new StringReader(fullText.toString()))));
	    if(!defs.isEmpty()) {
		doc.add(new Field("defs",dummy));
	    }
	    if(!refs.isEmpty()) {
		doc.add(new Field("refs",dummy));
	    }
	} catch (IOException e) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to read from ZIP ", e);
	}
    }
    
    public TokenStream tokenStream(String fieldName, Reader reader) {
	if("defs".equals(fieldName)) {
	    return new List2TokenStream(defs);
	} else if ( "refs".equals(fieldName)) {
	    return new List2TokenStream(refs);
	} else if ("full".equals(fieldName)) {
	    return new PlainFullTokenizer(reader);
	}
	return super.tokenStream(fieldName, reader);
    }
    
    /**
     * Write a cross referenced HTML file.
     * @param out Writer to write HTML cross-reference
     */
    public void writeXref(Writer out) throws IOException {
	out.write(xref.toString());
    }
}
