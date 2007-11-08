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
 * ident	"@(#)Util.java 1.2     05/12/01 SMI"
 */

package org.opensolaris.opengrok.web;

import java.util.regex.*;
import java.text.*;
import java.io.*;
import org.opensolaris.opengrok.history.Annotation;

/**
 * File for useful functions
 */
public class Util {
    /**
     * Return a string which represents a <code>CharSequence</code> in HTML.
     *
     * @param q a character sequence
     * @return a string representing the character sequence in HTML
     */
    public static String Htmlize(CharSequence q) {
        StringBuilder sb = new StringBuilder(q.length() * 2);
        Htmlize(q, sb);
        return sb.toString();
    }

    /**
     * Append a character sequence to an <code>Appendable</code> object. Escape
     * special characters for HTML.
     *
     * @param q a character sequence
     * @param out the object to append the character sequence to
     * @exception IOException if an I/O error occurs
     */
    public static void Htmlize(CharSequence q, Appendable out)
            throws IOException {
        for (int i = 0; i < q.length(); i++) {
            Htmlize(q.charAt(i), out);
        }
    }

    /**
     * Append a character sequence to a <code>StringBuilder</code>
     * object. Escape special characters for HTML. This method is identical to
     * <code>Htmlize(CharSequence,Appendable)</code>, except that it is
     * guaranteed not to throw <code>IOException</code> because it uses a
     * <code>StringBuilder</code>.
     *
     * @param q a character sequence
     * @param out the object to append the character sequence to
     * @see #Htmlize(CharSequence, Appendable)
     */
    public static void Htmlize(CharSequence q, StringBuilder out) {
        try {
            Htmlize(q, (Appendable) out);
        } catch (IOException ioe) {
            // StringBuilder's append methods are not declared to throw
            // IOException, so this should never happen.
            throw new RuntimeException(
                "StringBuilder should not throw IOException", ioe);
        }
    }

    public static void Htmlize(char[] cs, int length, Appendable out)
            throws IOException {
        for(int i=0; i < length && i < cs.length; i++) {
            Htmlize(cs[i], out);
        }
    }

    /**
     * Append a character to a an <code>Appendable</code> object. If the
     * character has special meaning in HTML, append a sequence of characters
     * representing the special character.
     *
     * @param c the character to append
     * @param out the object to append the character to
     * @exception IOException if an I/O error occurs
     */
    private static void Htmlize(char c, Appendable out) throws IOException {
        switch (c) {
        case '&': out.append("&amp;"); break;
        case '>': out.append("&gt;"); break;
        case '<': out.append("&lt;"); break;
        case '\n': out.append("<br/>"); break;
        default: out.append(c);
        }
    }

    public static String breadcrumbPath(String urlPrefix, String l) {
        return breadcrumbPath(urlPrefix, l, '/');
    }
    
    public static String breadcrumbPath(String urlPrefix, String l, char sep) {
        if(l == null || l.length() <=1 )
            return l;
        StringBuilder hyperl = new StringBuilder(20);
        if(l.charAt(0) == sep) {
            hyperl.append(sep);
        }
        int s = 0,e = 0;
        while((e = l.indexOf(sep, s)) >= 0) {
            if(e-s>0){
                hyperl.append("<a href=\"" + urlPrefix);
                hyperl.append(l.substring(0,e));
                hyperl.append("/\">");
                hyperl.append(l.substring(s,e));
                hyperl.append("</a>");
                hyperl.append(sep);
            }
            s = e+1;
        }
        if (s < l.length()) {
            hyperl.append("<a href=\"" + urlPrefix);
            hyperl.append(l);
            hyperl.append("\">");
            hyperl.append(l.substring(s, l.length()));
            hyperl.append("</a>");
        }
        return hyperl.toString();
    }
    
    public static String redableSize(long num) {
        float l = (float) num;
        NumberFormat formatter = new DecimalFormat("#,###,###,###.#");
        if ( l < 1024 ) {
            return formatter.format(l);
        } else if ( l < 1048576 ) {
            return (formatter.format(l / 1024) + "K");
        } else {
            return ("<b>" + formatter.format(l / 1048576)+ "M</b>");
        }
    }
    
    public static void readableLine(int num, Writer out, Annotation annotation)
            throws IOException {
        String snum = String.valueOf(num);
        if (num > 1) {
            out.write("\n");
        }
        out.write("<a class=\"");
        out.write((num % 10 == 0 ? "hl" : "l"));
        out.write("\" name=\"");
        out.write(snum);
        out.write("\">");
        out.write((num > 999 ? "   " : (num > 99 ? "    " : (num > 9 ? "     " : "      "))));
        out.write(snum);
        out.write(" </a>");
        if (annotation != null) {
            String r = annotation.getRevision(num);
            out.write("<span class=\"l\"> ");
            for (int i = r.length(); i < annotation.getWidestRevision(); i++) {
                out.write(" ");
            }
            
            out.write("<a href=\"");
            out.write(URIEncode(annotation.getFilename()));
            out.write("?a=true&r=");
            out.write(URIEncode(r));
            out.write("\">");
            Htmlize(r, out);
            out.write("</a>");            
            out.write(" </span>");

            String a = annotation.getAuthor(num);
            out.write("<span class=\"l\"> ");
            for (int i = a.length(); i < annotation.getWidestAuthor(); i++) {
                out.write(" ");
            }
            Htmlize(a, out);
            out.write(" </span>");
        }
    }
    
    /**
     * Append path and date into a string in such a way that lexicographic
     * sorting gives the same results as a walk of the file hierarchy.  Thus
     * null (\u0000) is used both to separate directory components and to
     * separate the path from the date.
     */
    public static String uid(String path, String date) {
        return path.replace('/', '\u0000') + "\u0000" + date;
    }
    
    public static String uid2url(String uid) {
        String url = uid.replace('\u0000', '/'); // replace nulls with slashes
        return url.substring(0, url.lastIndexOf('/')); // remove date from end
    }
    
    private static char[] hexdigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    
    public static String URIEncode(String q) {
        StringBuilder sb = new StringBuilder();
        char c;
        for(int i=0; i < q.length() ; i++) {
            c = q.charAt(i);
            if ((c >= 'a' && c <= 'z')
            ||(c >= 'A' && c <= 'Z')
            ||(c >= '0' && c <= '9')) {
                sb.append(c);
            } else {
                sb.append("%");
                sb.append(hexdigits[(0xf0 & c) >>> 4 ]);
                sb.append(hexdigits[0x0f & c]);
            }
        }
        return sb.toString();
    }
    
    public static String formQuoteEscape(String q) {
        if (q == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        char c;
        for(int i=0; i < q.length() ; i++) {
            c = q.charAt(i);
            if(c == '"') {
                sb.append("&quot;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    public static String FileType(String filename) {
        return null;
    }
    
    public static File getRCSFile(File file) {
        return getRCSFile(file.getParent(), file.getName());
    }
    
    public static File getRCSFile(String parent, String name) {
        File rcsDir = new File(parent + File.separator + "RCS");
        File rcsFile = new File(rcsDir, name + ",v");
        if (rcsFile.exists()) {
            return rcsFile;
        }
        // not RCS, try CVS instead
        return getCVSFile(parent, name);
    }

    private static File getCVSFile(String parent, String name) {
        try{
            File CVSdir = new File(parent + "/CVS");
            if(CVSdir.isDirectory() && CVSdir.canRead()) {
                File root = new File(CVSdir, "Root");
                if (root.canRead()) {
                    BufferedReader rootReader = new BufferedReader(new FileReader(root));
                    String cvsroot = rootReader.readLine();
                    if(cvsroot.startsWith("/")) {
                        File repository = new File(CVSdir, "Repository");
                        BufferedReader repoReader = new BufferedReader(new FileReader(repository));
                        String repo = repoReader.readLine();
                        repoReader.close();
                        rootReader.close();
                        String dir = cvsroot + File.separatorChar + repo;
                        String filename = name + ",v";
                        File rcsFile = new File(dir, filename);
                        if (!rcsFile.exists()) {
                            File atticFile = new File(dir + File.separatorChar + "Attic", filename);
                            if (atticFile.exists())
                                rcsFile = atticFile;
                        }
                        return rcsFile;
                    }
                    rootReader.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static File getSCCSFile(File file) {
        return getSCCSFile(file.getParent(), file.getName());
    }

    public static File getSCCSFile(String parent, String name) {
        return new File(parent + "/SCCS/s." + name);
    }
    
}
