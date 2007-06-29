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
 * ident	"@(#)PathIndex.java 1.1     05/11/11 SMI"
 */

package org.opensolaris.opengrok.index;

import java.io.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.analysis.standard.*;

/**
 * Creates an Index of file paths
 */
class PathIndex {
    public static void main(String argv[]) {
        try{
            IndexWriter writer = new IndexWriter(argv[0], new StandardAnalyzer(), true);
            String path;
            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
            int i = 0;
            while ((path = stdin.readLine()) != null) {
                int lastSlash = path.lastIndexOf('/');
                String parent = (lastSlash != -1) ? path.substring(0, lastSlash) : "";
                //System.out.println(parent);
                Document doc = new Document();
                doc.add(new Field("p", parent, Field.Store.YES, Field.Index.TOKENIZED));
                writer.addDocument(doc);
                i++;
            }
            writer.optimize();
            writer.close();
            System.out.println("Added " + i + " directory paths");
            
        } catch (Exception e) {
            System.err.println("Usage: PathInder indexDirectory < path per line on std in");
            e.printStackTrace();
        }
    }
}
