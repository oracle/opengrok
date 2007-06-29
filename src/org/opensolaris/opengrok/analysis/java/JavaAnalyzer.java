/*
 * JavaAnalyzer.java
 *
 * Created on July 13, 2006, 9:44 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.opensolaris.opengrok.analysis.java;

import org.apache.lucene.document.*;
import org.apache.lucene.analysis.*;
import java.io.*;
import org.opensolaris.opengrok.analysis.*;
import org.opensolaris.opengrok.analysis.plain.*;
import org.opensolaris.opengrok.history.Annotation;

/**
 *
 * @author martin
 */
public class JavaAnalyzer extends PlainAnalyzer {

    JavaSymbolTokenizer cref;
    JavaXref xref;
    Reader dummy = new StringReader("");

    public static String[] suffixes = {
        "JAVA"
    };
    public static String[] magics = {
        "/*"
    };
    /** Creates a new instance of JavaAnalyzer */
    public JavaAnalyzer() {
        super();
        cref = new JavaSymbolTokenizer(dummy);
        xref = new JavaXref(dummy);
    }

    public void analyze(Document doc, InputStream in) {
        super.analyze(doc, in);
        doc.add(new Field("refs", dummy));
    }    
    
    public TokenStream tokenStream(String fieldName, Reader reader) {
        if("refs".equals(fieldName)) {
            cref.reInit(super.content, super.len);
            return cref;
        }
        return super.tokenStream(fieldName, reader);
    }
    
    public static String[] getMagics() {
        return magics;
    }
    
    public static String[] getSuffixes() {
        return suffixes;
    }

    /**
     * Write a cross referenced HTML file.
     * @param out Writer to write HTML cross-reference
     */
    public void writeXref(Writer out) throws IOException {
        xref.reInit(content, len);
        xref.setDefs(defs);
        xref.write(out);
        //lines = xref.getLine();
    }
    
    /**
     * Write a cross referenced HTML file reads the source from in
     * @param in Input source
     * @param out Output xref writer
     * @param annotation annotation for the file (could be null)
     */
    public static void writeXref(InputStream in, Writer out,
                                 Annotation annotation) throws IOException {
        JavaXref xref = new JavaXref(in);
        xref.annotation = annotation;
        xref.write(out);
    }
}
