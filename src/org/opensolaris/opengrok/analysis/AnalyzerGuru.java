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
 * ident	"@(#)AnalyzerGuru.java 1.3     06/02/22 SMI"
 */
package org.opensolaris.opengrok.analysis;

import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.analysis.document.TroffAnalyzer;
import org.opensolaris.opengrok.analysis.java.JavaAnalyzer;
import org.opensolaris.opengrok.analysis.plain.*;
import org.opensolaris.opengrok.analysis.c.*;
import org.opensolaris.opengrok.analysis.sh.*;
import org.opensolaris.opengrok.analysis.data.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import org.apache.lucene.document.*;
import org.apache.lucene.analysis.*;
import org.opensolaris.opengrok.analysis.archive.*;
import org.opensolaris.opengrok.analysis.executables.*;
import org.opensolaris.opengrok.history.*;
import org.opensolaris.opengrok.web.Util;

/**
 * Manages and porvides Analyzers as needed.
 * Created on September 22, 2005
 *
 * @author Chandan
 */
public class AnalyzerGuru {
    private static HashMap<String, Class> ext;
    private static SortedMap<String, Class> magics;
    private static ArrayList<Method> matchers;
    /*
     * If you write your own analyzer please register it here
     */
    private static Class[] analyzers = {
        IgnorantAnalyzer.class,
        BZip2Analyzer.class,
        FileAnalyzer.class,
        XMLAnalyzer.class,
        TroffAnalyzer.class,
        ELFAnalyzer.class,
        JavaClassAnalyzer.class,
        ImageAnalyzer.class,
        JarAnalyzer.class,
        ZipAnalyzer.class,
        TarAnalyzer.class,
        CAnalyzer.class,
        ShAnalyzer.class,
        PlainAnalyzer.class,
        GZIPAnalyzer.class,
        JavaAnalyzer.class
    };
    private static HashMap<Class, FileAnalyzer> analyzerInstances = new HashMap<Class, FileAnalyzer>();
    
    /**
     * Initializes an AnalyzerGuru
     */
    static {
        if (ext == null) {
            ext = new HashMap<String, Class>();
        }
        if (magics == null) {
            magics = new TreeMap<String, Class>();
            // TODO: have a comparator
        }
        if (matchers == null) {
            matchers = new ArrayList<Method>();
        }
        for (Class analyzer: analyzers) {
            try{
                String[] suffixes = (String[]) analyzer.getField("suffixes").get(null);
                for (String suffix: suffixes) {
                    //System.err.println(analyzer.getSimpleName() + " = " + suffix);
                    ext.put(suffix, analyzer);
                }
            } catch (Exception e) {
                //   System.err.println("AnalyzerFinder:" + analyzer.getSimpleName() + e);
            }
            try{
                String[] smagics = (String[]) analyzer.getField("magics").get(null);
                for (String magic: smagics) {
                    //System.err.println(analyzer.getSimpleName() + " = " + magic);
                    magics.put(magic, analyzer);
                }
            } catch (Exception e) {
                //  System.err.println("AnalyzerFinder: " + analyzer.getSimpleName() + e);
            }
            try{
                Method m = analyzer.getMethod("isMagic", byte[].class);
                if (m != null) matchers.add(m);
            } catch (Exception e) {
            }
        }
        //System.err.println("Exts " + ext);
        //System.err.println("Matchers " + matchers);
    }
    
    /*
     * Get the default Analyzer.
     */
    public static FileAnalyzer getAnalyzer() {
        
        Class a = FileAnalyzer.class;
        FileAnalyzer fa = analyzerInstances.get(a);
        if (fa == null) {
            try {
                fa = (FileAnalyzer) a.newInstance();
                analyzerInstances.put(a, fa);
                return fa;
            } catch (Exception e) {
                System.err.println("ERROR: Initializing " + a);
            }
        }
        return fa;
    }
    
    /*
     * use this if you want to analyze a file. Analyzers are costly.
     */
    public static FileAnalyzer getAnalyzer(InputStream in, String path) throws IOException {
        Class a = find(in, path);
        if(a == null) {
            a = FileAnalyzer.class;
        }
        if (a != null) {
            FileAnalyzer fa = analyzerInstances.get(a);
            if (fa == null) {
                try {
                    fa = (FileAnalyzer) a.newInstance();
                    analyzerInstances.put(a, fa);
                    return fa;
                } catch (Exception e) {
                    System.err.println("ERROR: Initializing " + a);
                }
            } else {
                return fa;
            }
        }
        return null;
    }
    
    public Document getDocument(File f, InputStream in, String path) throws IOException {
        Document doc = new Document();
        String date = DateField.timeToString(f.lastModified());
        doc.add(new org.apache.lucene.document.Field("u", Util.uid(path, date), false, true, false));
        doc.add(new org.apache.lucene.document.Field("fullpath", f.getAbsolutePath(), true, true, true));
        try{
            HistoryReader hr = HistoryGuru.getInstance().getHistoryReader(f);
            if (hr != null) {
                doc.add(org.apache.lucene.document.Field.Text("hist", hr));
                // date = hr.getLastCommentDate() //RFE
            }
        } catch (IOException e) {
        }
        doc.add(org.apache.lucene.document.Field.Keyword("date", date));
        if(path != null) {
            doc.add(new org.apache.lucene.document.Field("path", path, true, true, true));
        }
        FileAnalyzer fa = null;
        try {
            fa = getAnalyzer(in, path);
        } catch (Exception e) {
            
        }
        if (fa != null) {
            try {
                Genre g  = fa.getGenre();
                if (g == Genre.PLAIN) {
                    doc.add(new org.apache.lucene.document.Field("t", "p", true, false, false));
                } else if ( g == Genre.XREFABLE) {
                    doc.add(new org.apache.lucene.document.Field("t", "x", true, false, false));
                } else if ( g == Genre.HTML) {
                    doc.add(new org.apache.lucene.document.Field("t", "h", true, false, false));
                }
                fa.analyze(doc, in);
            } catch (Exception e) {
                // Ignoring any errors while analysing
            }
        }
        doc.removeField("fullpath");
        return doc;
    }
    
    /**
     * @return The contentType suitable for printing to response.setContentType()
     */
    public static String getContentType(String path) {
        Class a = find(path);
        return  getContentType(a);
    }
    
    public static String getContentType(InputStream in, String path) throws IOException {
        Class a = find(in, path);
        return getContentType(a);
    }
    
    public static String getContentType(Class a) {
        String contentType = null;
        if (a != null) {
            try {
                contentType = (String) a.getMethod("getContentType").invoke(null);
            } catch (Exception e ) {
                
            }
        }
        return contentType;
    }
    
    public static void writeXref(Class a, InputStream in, Writer out) throws IOException {
        if (a != null) {
            try {
                a.getMethod("writeXref", InputStream.class, Writer.class).invoke(null, in, out);
            } catch (IllegalArgumentException ex) {
            } catch (SecurityException ex) {
            } catch (NoSuchMethodException ex) {
            } catch (InvocationTargetException ex) {
            } catch (IllegalAccessException ex) {
            }
        }
    }
    
    /**
     * @return The genre suitable to decide how to display the file
     */
    public static Genre getGenre(String path) {
        Class a = find(path);
        return  getGenre(a);
    }
    
    public static Genre getGenre(InputStream in, String path) throws IOException {
        Class a = find(in, path);
        return getGenre(a);
    }
    
    public static Genre getGenre(InputStream in) throws IOException {
        Class a = find(in);
        return getGenre(a);
    }
    
    public static Genre getGenre(Class a) {
        Genre g = null;
        if (a != null) {
            try {
                g = (Genre) a.getField("g").get(null);
            } catch (Exception e ) {
                e.printStackTrace();
            }
        }
        return g;
    }
    
    /**
     * Finds a suitable analyser class for an InputStream and a file name
     * Use if you just want to find file type.
     */
    public static Class find(InputStream in, String path) throws IOException {
        Class a = find(path);
        if(a == null) {
            a = find(in);
        }
        return a;
    }
    
    public static Class find(String path) {
        int i = 0;
        if ((i = path.lastIndexOf('/')) > 0 || (i = path.lastIndexOf('\\')) > 0) {
            if(i+1<path.length())
                path = path.substring(i+1);
        }
        path = path.toUpperCase();
        int dotpos = path.lastIndexOf('.');
        if(dotpos >= 0) {
            Class analyzer = ext.get(path.substring(dotpos+1).toUpperCase());
            if (analyzer != null) {
                //System.err.println(path.substring(dotpos+1).toUpperCase() + " = " + analyzer.getSimpleName());
                return analyzer;
            }
        }
        return(ext.get(path));
    }
    
    public static Class find(InputStream in) throws IOException {
        in.mark(8);
        byte[] content = new byte[8];
        int len = in.read(content);
        in.reset();
        if (len < 4)
            return null;
        Class a = find(content);
        if(a == null) {
            for(Method matcher: matchers) {
                try {
                    //System.out.println("USING = " + matcher.getName());
                    if ((a = (Class) matcher.invoke(null, content))!= null) {
                        return a;
                    }
                } catch (Exception e ) {
                    e.printStackTrace();
                }
            }
        }
        return a;
    }
    
    public static Class find(byte[] content) {
        char[] chars = new char[content.length > 8 ? 8 : content.length];
        for (int i = 0; i< chars.length ; i++) {
            chars[i] = (char)(0xFF & content[i]);
        }
        return(findMagic(new String(chars)));
    }
    
    public static Class findMagic(String content) {
        Class a = magics.get(content);
        if (a == null) {
            for(String magic: magics.keySet()) {
                if(content.startsWith(magic)) {
                    return magics.get(magic);
                }
            }
        }
        return a;
    }
    
    public static void main(String [] args) throws Exception {
        AnalyzerGuru af = new AnalyzerGuru();
        System.out.println("<pre wrap=true>");
        for(String arg: args) {
            try {
                Class an = af.find(arg);
                File f = new File(arg);
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
                FileAnalyzer fa = af.getAnalyzer(in, arg);
                System.out.println("\nANALYZER = " + fa);
                Document doc = af.getDocument(f, in, arg);
                System.out.println("\nDOCUMENT = " + doc);
                Enumeration fields = doc.fields();
                while (fields.hasMoreElements()) {
                    org.apache.lucene.document.Field field = (org.apache.lucene.document.Field) fields.nextElement();
                    if(field.isTokenized()){
                        Reader r = field.readerValue();
                        if(r == null) {
                            r = new StringReader(field.stringValue());
                        }
                        TokenStream ts = fa.tokenStream(field.name(), r);
                        System.out.println("\nFIELD = " + field.name() + " TOKEN STREAM = "+ ts.getClass().getName());
                        Token t;
                        while((t = ts.next()) != null) {
                            System.out.print(t.termText());
                            System.out.print(' ');
                        }
                        System.out.println();
                    }
                    if(field.isStored()) {
                        System.out.println("\nFIELD = " + field.name());
                        if(field.readerValue() == null) {
                            System.out.println(field.stringValue());
                        } else {
                            System.out.println("STORING THE READER");
                        }
                    }
                }
                System.out.println("Writing XREF--------------");
                Writer out = new OutputStreamWriter(System.out);
                fa.writeXref(out);
                out.flush();
            } catch (Exception e) {
                System.err.println("ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
