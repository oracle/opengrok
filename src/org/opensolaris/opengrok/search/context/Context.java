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

/**
 * This is supposed to get the matching lines from sourcefile.
 * since lucene does not easily give the match context.
 */
package org.opensolaris.opengrok.search.context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;
import org.opensolaris.opengrok.analysis.CompatibleAnalyser;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.search.Hit;
import org.opensolaris.opengrok.web.Util;


public class Context {
    private LineMatcher[] m;
    private int MAXFILEREAD = 32768;
    private char[] buffer;
    PlainLineTokenizer tokens;
    String queryAsURI;
    private static Set<String> tokenFields = new HashSet<String>(3);
    static {
        tokenFields.add("full");
        tokenFields.add("refs");
        tokenFields.add("defs");
    }
    
    /**
     * Constructs a context generator
     */
    public Context(Query query) {
        QueryMatchers qm = new QueryMatchers();
        m = qm.getMatchers(query, tokenFields);
        if(m != null) {
        queryAsURI = Util.URIEncode(query.toString());
        //System.err.println("Found Matchers = "+ m.length + " for " + query);
        buffer  = new char[MAXFILEREAD];
        tokens = new PlainLineTokenizer((Reader)null);
        }
    }
    public boolean isEmpty() {
        return m == null;
    }    
    /**
     *
     * @param in File to be matched
     * @param out to write the context
     * @param morePrefix to link to more... page
     * @param path path of the file
     * @param tags format to highlight defs.
     * @param limit should the number of matching lines be limited?
     * @return Did it get any matching context?
     */
    private boolean alt = true;
    public boolean getContext(Reader in, Writer out, String urlPrefix, String morePrefix, String path, String tags, boolean limit, List<Hit> hits) {
        alt = !alt;
        if (m == null) {
            return false;
        }
        boolean anything = false;
        TreeMap<Integer, String[]> matchingTags = null;
        if (tags != null) {
            BufferedReader ctagsIn = new BufferedReader(new StringReader(tags)); //XXX
            String tagLine;
            matchingTags = new TreeMap<Integer, String[]>();
            try {
                while ((tagLine = ctagsIn.readLine()) != null) {
                    //System.err.println(" read tagline : " + tagLine);
                    int p = tagLine.indexOf('\t');
                    if (p > 0) {
                        String tag = tagLine.substring(0, p);//.toLowerCase();
                        //System.err.println(" matching " + tag);
                        for (int i = 0; i< m.length; i++) {
                            if (m[i].match(tag) == LineMatcher.MATCHED) {
                                /*
                                 * desc[1] is line number
                                 * desc[2] is type
                                 * desc[3] is  matching line;
                                 */
                                String desc[] = tagLine.split("\t",4);
                                if (in != null) {
                                    matchingTags.put(new Integer(desc[1]), desc);
                                } else {
                                    if (out != null) {
                                        out.write("<a class=\"s\" href=\"");
                                        out.write(urlPrefix);
                                        out.write(path);
                                        out.write("#");
                                        out.write(desc[1]);
                                        out.write("\"><span class=\"l\">");
                                        out.write(desc[1]);
                                        out.write("</span> ");
                                        out.write(Util.Htmlize(desc[3]).replaceAll(desc[0], "<b>" + desc[0] + "</b>"));
                                        out.write("</a> <i> ");
                                        out.write(desc[2]);
                                        out.write(" </i><br/>");
                                    } else  {
                                        Hit hit = new Hit(path, Util.Htmlize(desc[3]).replaceAll(desc[0], "<b>" + desc[0] + "</b>"), desc[1], false, alt);
                                        hits.add(hit);
                                        anything = true;
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (hits != null) {
                    // @todo verify why we ignore all exceptions?
                    e.printStackTrace();
                }
            }
        }
        /**
         * Just to get the matching tag send a null in
         */ 
        if(in == null) {
            return anything;
        }
        int charsRead = 0;
        boolean truncated = false;
        
        if (!RuntimeEnvironment.getInstance().isQuickContextScan()) {
            limit = false;
        }
        
        if (limit) {
            try{
                charsRead = in.read(buffer);
                // truncate to last line read
                if (charsRead == MAXFILEREAD) {
                    for(int i = charsRead - 1; i > charsRead-100; i--) {
                        if(buffer[i] == '\n') {
                            charsRead = i;
                            truncated = true;
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                return anything;
            }
            if(charsRead == 0) {
                return anything;
            }
            
            tokens.reInit(buffer, charsRead, out, urlPrefix + path + "#", matchingTags);
        } else {
            tokens.reInit(in, out, urlPrefix + path + "#", matchingTags);
        }
        
        if (hits != null) {
            tokens.setAlt(alt);
            tokens.setHitList(hits);
            tokens.setFilename(path);
        }
        
        try {
            String token;
            int matchState = LineMatcher.NOT_MATCHED;
            int matchedLines = 0;
            while ((token = tokens.next()) != null && (!limit || matchedLines < 10)) {
                for (int i = 0; i< m.length; i++) {
                    matchState = m[i].match(token);
                    if (matchState == LineMatcher.MATCHED) {
                        tokens.printContext();
                        matchedLines++;
                        //out.write("<br> <i>Matched " + token + " maxlines = " + matchedLines + "</i><br>");
                        break;
                    } else if ( matchState == LineMatcher.WAIT) {
                        tokens.holdOn();
                    } else {
                        tokens.neverMind();
                    }
                }
            }
            anything = matchedLines > 0;
            tokens.dumpRest();
            if (limit && (truncated || matchedLines == 10)) {
                if (out != null) {
                    out.write("&nbsp; &nbsp; [<a href=\"" + morePrefix + path + "?t=" +  queryAsURI + "\">all</a>...]");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
                if (out != null) {
                    out.flush();
                }
            } catch (Exception e) {	}
        }
        return anything;
    }
    
    public static void main(String[] args) {
        try{
            QueryParser parser = new QueryParser("full", new CompatibleAnalyser());
            Context ctx = new Context(parser.parse(args[0]));
            Date start = new Date();
            Writer out = new BufferedWriter(new OutputStreamWriter(System.out));
            ctx.getContext(new BufferedReader(new FileReader(args[1])), out, null, null, args[1], null, false, null);
            long span =  ((new Date()).getTime() - start.getTime());
            System.err.println("took: "+ span + " msec");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
