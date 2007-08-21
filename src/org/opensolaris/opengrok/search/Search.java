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

package org.opensolaris.opengrok.search;

import java.io.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.queryParser.*;
import org.opensolaris.opengrok.analysis.CompatibleAnalyser;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 * Search and list the matching files
 */
class Search {
    
    /**
     * usage Search index "query" prunepath
     */
    public static void main(String[] argv) {
	String usage = "USAGE: Search DATA_ROOT [-d | -r | -p | -h] 'query string' ..\n"
	    + "\t DATA_ROOT is the directory where OpenGrok data files are stored\n"
	    + "\t -d Symbol Definitions\n"
	    + "\t -r Symbol References\n"
	    + "\t -p Path\n"
	    + "\t -h History"
	    ;
	if (argv == null || argv.length < 2) {
	    System.out.println(usage);
	    System.exit(1);
	}
	StringBuffer q = new  StringBuffer("");
	StringBuffer defs = new  StringBuffer("");
	StringBuffer refs = new  StringBuffer("");
	StringBuffer path = new  StringBuffer("");
	StringBuffer hist = new  StringBuffer("");
	String DATA_ROOT = null;
	
	for (int i = 0; i < argv.length ; i++) {
	    if (argv[i].equals("-d")) {
		if(i+1 < argv.length) {
		    defs.append(argv[++i] + ' ');
		}
	    } else if (argv[i].equals("-r")) {
		if(i+1 < argv.length) {
		    refs.append(argv[++i] + ' ');
		}
	    } else if (argv[i].equals("-p")) {
		if(i+1 < argv.length) {
		    path.append(argv[++i] + ' ');
		}
	    } else if (argv[i].equals("-h")) {
		if(i+1 < argv.length) {
		    hist.append(argv[++i] + ' ');
		}
	    } else if (!argv[i].startsWith("-")) {
		if (DATA_ROOT == null) {
		    DATA_ROOT = argv[i];
		} else {
		    q.append(argv[i] + ' ');
		}
	    } else {
		System.out.println(usage);
		System.exit(1);
	    }
	}
	
	if (q!= null && q.equals("")) q = null;
	if (defs != null && defs.length() == 0) defs = null;
	if (refs != null && refs.length() == 0) refs = null;
	if (hist != null && hist.length() == 0) hist = null;
	if (path != null && path.length() == 0) path = null;
	
	if (q != null || defs != null || refs != null || hist != null || path != null) {
	    try{
		CompatibleAnalyser analyzer = new CompatibleAnalyser();
		String qstr =   (q == null ? "" : q ) +
		    (defs == null ? "" : " defs:(" + defs+")") +
		    (refs == null ? "" : " refs:(" + refs+")") +
		    (path == null ? "" : " path:(" + path+")") +
		    (hist == null ? "" : " hist:(" + hist+")");
		
		QueryParser qparser = new QueryParser("full", analyzer);
		qparser.setDefaultOperator(QueryParser.AND_OPERATOR);
                qparser.setAllowLeadingWildcard(RuntimeEnvironment.getInstance().isAllowLeadingWildcard());
		Query query = qparser.parse(qstr); //parse the
		File src_root = new File(DATA_ROOT, "SRC_ROOT");
		String SRC_ROOT = "";
		try {
		    if(src_root.canRead()) {
			BufferedReader br = new BufferedReader(new FileReader(src_root));
			SRC_ROOT = br.readLine();
			if(!(new File(SRC_ROOT)).isDirectory()) {
			    SRC_ROOT = "";
			}
		    } else {
			SRC_ROOT = "";
		    }
		} catch (Exception e) {
		    e.printStackTrace();
		    SRC_ROOT = "";
		}
		IndexReader ireader = IndexReader.open(DATA_ROOT + "/index");
		Searcher searcher = new IndexSearcher(ireader);
		Hits hits = searcher.search(query);
		
		if (hits.length() == 0) {
		    System.err.println("Your search  "+ query.toString() + " did not match any files.");
		}
		for (int i = 0; i < hits.length(); i++) {
		    String rpath = hits.doc(i).get("path");
		    System.out.println(SRC_ROOT + rpath);
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }
}
