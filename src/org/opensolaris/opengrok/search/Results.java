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
 * ident      "@(#)Results.java 1.2     06/02/22 SMI"
 */

package org.opensolaris.opengrok.search;

import java.io.*;
import java.util.*;

import org.apache.lucene.search.*;
import org.apache.lucene.document.*;
import org.opensolaris.opengrok.analysis.TagFilter;
import org.opensolaris.opengrok.web.*;
import org.opensolaris.opengrok.search.context.HistoryContext;
import org.opensolaris.opengrok.search.context.Context;

public class Results {
    public static void prettyPrintHTML(Hits hits, int start, int end, Writer out,
            Context sourceContext, HistoryContext historyContext,
            Summarizer summer, String urlPrefix,
            String morePrefix,
            String srcRoot,
            String dataRoot,
            EftarFileReader desc
            ) throws IOException {
        char[] content = new char[1024*8];
        LinkedHashMap<String, ArrayList<Document>> dirHash = new LinkedHashMap<String, ArrayList<Document>>();
        for (int i = start; i < end; i++) {
            Document doc = hits.doc(i);
            String rpath = doc.get("path");
            String parent = rpath.substring(0,rpath.lastIndexOf('/'));
            ArrayList<Document> dirDocs = dirHash.get(parent);
            if(dirDocs == null) {
                dirDocs = new ArrayList<Document>();
                dirHash.put(parent, dirDocs);
            }
            dirDocs.add(doc);
        }
        
        for (Map.Entry<String, ArrayList<Document>> entry: dirHash.entrySet()) {
            String parent = entry.getKey();
            String tag = (desc != null) ? " - <i>" + desc.get(parent) + "</i>": "";
            
            out.write("<tr class=\"dir\"><td colspan=\"2\">&nbsp;&nbsp;<a href=\"");
            out.write(Util.URIEncodePath(urlPrefix + parent));
            out.write("/\">" + parent + "/</a>" + tag + "</td></tr>");

            boolean alt = false;
            for (Document doc: entry.getValue()) {
                String rpath = doc.get("path");
                String self = rpath.substring(rpath.lastIndexOf('/')+1, rpath.length());
                String selfUrl = Util.URIEncodePath(urlPrefix + rpath);
                out.write("<tr ");
                if(alt)
                    out.write(" class=\"alt\"");
                alt ^= true;
                out.write("><td class=\"f\"><a href=\"" +
                        selfUrl + "\">"+self+"</a>&nbsp;</td><td><tt class=\"con\">");
                if (sourceContext != null) {
                    String genre = doc.get("t");
                    String tags = doc.get("tags");
                    try {
                        if ("p".equals(genre) && srcRoot != null) {
                            sourceContext.getContext(new FileReader(srcRoot + rpath), out, urlPrefix, morePrefix, rpath,
                                    tags, true, null);
                        } else if("x".equals(genre) && dataRoot != null && summer != null) {
                            int len;
                            Reader r = new TagFilter(new BufferedReader(new FileReader(dataRoot + "/xref" + rpath)));
                            try {
                                len = r.read(content);
                            } finally {
                                r.close();
                            }
                            out.write(summer.getSummary(new String(content, 0, len)).toString());
                        } else if("h".equals(genre) && srcRoot != null && summer != null){
                            int len;
                            Reader r = new TagFilter(new BufferedReader(new FileReader(srcRoot + rpath)));
                            try {
                                len = r.read(content);
                            } finally {
                                r.close();
                            }
                            out.write(summer.getSummary(new String(content, 0, len)).toString());
                        } else {
                            sourceContext.getContext(null, out, urlPrefix, morePrefix, rpath, tags, true, null);
                        }
                    } catch (IOException e) {
                        
                    }
                    //out.write("Genre = " + genre);
                }
                if(historyContext != null) {
                    historyContext.getContext(srcRoot + parent, self, rpath, out);
                }
                out.write("</tt></td></tr>\n");
            }
        }
    }
}
