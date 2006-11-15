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
 * Copyright 2005 Trond Norbye.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)IndexDatabase.java 1.1     06/02/22 SMI"
 */

package org.opensolaris.opengrok.search.scope;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author Trond Norbye
 */
public class IndexDatabase {
    /**
     * Holds value of property database.
     */
    private String database;
    
    /**
     * Holds value of property source.
     */
    private String source;
    
    /**
     * Creates a new instance of IndexDatabase
     */
    public IndexDatabase() {
        this(null, null);
    }
    /**
     * Creates a new instance of IndexDatabase
     */
    public IndexDatabase(String index) {
        this(index, null);
        File file = new File(index + File.separator + "SRC_ROOT");
        if (file.exists() && file.canRead()) {
            BufferedReader in = null; 
            try {
                in = new BufferedReader(new FileReader(file));
                this.source = in.readLine();
                in.close();
            } catch (IOException exp) {
                try {
                    in.close();
                } catch (IOException ex) {
                    ;
                }
            }
        }
    }
    /**
     * Creates a new instance of IndexDatabase
     */
    public IndexDatabase(String index, String source) {
        this.database = index;
        this.source = source;
    }
    
    /**
     * Getter for property database.
     *
     * @return Value of property database.
     */
    public String getDatabase() {
        return this.database;
    }
    
    /**
     * Setter for property database.
     *
     * @param database New value of property database.
     */
    public void setDatabase(String database) {
        this.database = database;
    }
    
    /**
     * Getter for property source.
     *
     * @return Value of property source.
     */
    public String getSource() {
        return this.source;
    }
    
    /**
     * Setter for property source.
     *
     * @param source New value of property source.
     */
    public void setSource(String source) {
        this.source = source;
    }

    public String toString() {
        String ret;
        if (source != null) {
            ret = source;
        } else {
            ret = "Database: " + database;
        }
        return ret;
    }

    public void update() throws Exception {
        
    }
    
    
}
