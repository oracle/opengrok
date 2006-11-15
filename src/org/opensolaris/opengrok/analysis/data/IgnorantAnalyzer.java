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
package org.opensolaris.opengrok.analysis.data;
import java.io.InputStream;
import org.apache.lucene.document.Document;
import org.opensolaris.opengrok.analysis.*;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;

/**
 * Analyzer that claims to analyze files which are 
 * mostly plain text data, but ignores them.
 * 
 * Created on September 30, 2005
 *
 * @author Chandan
 */
public class IgnorantAnalyzer extends FileAnalyzer {
    public static String[] suffixes = {
        "BDF", "XPM", "PS", "AFM", "PDF"
    };
    
    public static String[] magics = {
        "%!PS-",         //post script files
        "# PaCkAg",
        "%PDF"
    };

    public static Genre g = Genre.DATA;
    
    public Genre getGenre() {
        return this.g;
    }

    public void analyze(Document doc, InputStream in) {
    }

    /** Creates a new instance of IgnorantAnalyzer */
    public IgnorantAnalyzer() {
    }
}
