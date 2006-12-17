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
 * ident	"@(#)DirectoryHistoryReader.java 1.2     06/02/22 SMI"
 */
package org.opensolaris.opengrok.history;

import java.io.*;
import java.text.*;
import java.util.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryParser.*;
import org.apache.lucene.search.*;
import org.opensolaris.opengrok.analysis.CompatibleAnalyser;

/**
 * Comment that describes the contents of this DirectoryHistoryReader.java
 * Created on November 7, 2005
 *
 * @author Chandan
 */
public class DirectoryHistoryReader extends HistoryReader {
    public LinkedHashMap<Date, HashMap<String, HashMap<String, ArrayList<String>>>> hash
	= new LinkedHashMap<Date, HashMap<String, HashMap<String, ArrayList<String>>>>();
    Format df;
    Iterator<Date> diter;
    Date idate;
    Iterator<String> aiter;
    String iauthor;
    Iterator<String> citer;
    String icomment;
    
    /** Creates a new instance of DirectoryHistoryReader */
    public void DirectoryHistoryReader() {
	
    }
    
    public DirectoryHistoryReader(String index, String path, String src_root) throws IOException {
	IndexReader ireader = IndexReader.open(index);
	IndexSearcher searcher = new IndexSearcher(ireader);
	Sort sort = new Sort("date", true);
	QueryParser qparser = new QueryParser("path", new CompatibleAnalyser());
	Query query = null;
	Hits hits = null;
	try {
	    query = qparser.parse(path);
	    hits = searcher.search(query, sort);
	} catch (org.apache.lucene.queryParser.ParseException e){
	}
	if(hits != null) {
	    for(int i = 0; i< 40 && i < hits.length(); i++) {
		Document doc = hits.doc(i);
		String rpath = doc.get("path");
		Date cdate = DateField.stringToDate(doc.get("date"));
		String comment = "none", cauthor = "nobody";
		int ls = rpath.lastIndexOf('/');
		if(ls != -1) {
		    String rparent = (ls != -1) ? rpath.substring(0, ls) : "";
		    String rbase = rpath.substring(ls+1);
		    comment = rparent;
                    HistoryReader hr = null;
                    try {
                        File f = new File(src_root + rparent, rbase);
                        hr = HistoryGuru.getInstance().getHistoryReader(f);
                    } catch (IOException e) {
                    }
		    if(hr != null) {
                        try {
			while(hr.next()) {
			    if(hr.isActive()) {
				comment = hr.getComment();
				cauthor = hr.getAuthor();
				cdate = hr.getDate();
				put(cdate, cauthor, comment, rpath);
				break;
			    }
			}
			hr.close();
                        } catch (IOException e) {
                            put(cdate, "-", "", rpath);
                        }
		    } else {
			put(cdate, "-", "", rpath);
		    }
		}
	    }
	}
	searcher.close();
	ireader.close();
    }
    
    public void put(Date date, String author, String comment, String path) {
	long time = date.getTime();
	date.setTime(time - (time % 3600000l));	//
	HashMap<String, HashMap<String, ArrayList<String>>> ac;
	HashMap<String, ArrayList<String>> cf;
	ArrayList<String> fls;
	ac = hash.get(date);
	if(ac == null) {
	    ac = new HashMap<String, HashMap<String, ArrayList<String>>>();
	    hash.put(date, ac);
	}
	cf = ac.get(author);
	if(cf == null) {
	    cf = new HashMap<String, ArrayList<String>>();
	    ac.put(author, cf);
	}
	fls = cf.get(comment);
	if(fls == null) {
	    fls = new ArrayList<String>();
	    cf.put(comment, fls);
	}
	fls.add(path);
    }
    
    public void close() {
    }
    
    public static void main(String[] arg) throws Throwable {
	Date start = new Date();
	DirectoryHistoryReader dhr = new DirectoryHistoryReader(arg[0], arg[1], arg[2]);
	while(dhr.next()) {
	    System.out.println(dhr.getDate() + ":" + dhr.getAuthor() + " = " + dhr.getComment());
	}
	System.out.println("time taken = " + ((new Date()).getTime() - start.getTime()));
    }

    public boolean next() throws IOException {
	if (diter == null) {
	    diter = hash.keySet().iterator();
	}
	if(citer == null || !citer.hasNext()) {
	    if(aiter == null || !aiter.hasNext()) {
		if(diter.hasNext()) {
		    aiter = hash.get(idate = diter.next()).keySet().iterator();
		} else {
		    return false;
		}
	    }
	    citer = hash.get(idate).get(iauthor = aiter.next()).keySet().iterator();
	}
	icomment = citer.next();
	return true;
    }
    
    public String getLine() {
	return null;
    }
    
    public String getRevision() {
	return null;
    }
    
    public Date getDate() {
	return idate;
    }
    
    public String getAuthor() {
	return iauthor;
    }
    
    public String getComment() {
	return icomment;
    }
    
    public ArrayList<String> getFiles() {
	return hash.get(idate).get(iauthor).get(icomment);
    }
    
    public boolean isActive() {
	return true;
    }
}
