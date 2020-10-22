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
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.analysis.json;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Json (schema) keywords.
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();
    
    static {
        kwd.add("true");
        kwd.add("false");
        kwd.add("null");

//TODO below applies ONLY for schema - detect this is a schema and use keywords, not otherwise
    //json as such has no keywords
        
/*        
        kwd.add("title");
        kwd.add("description");
        kwd.add("default");
        kwd.add("enum");
        kwd.add("Boolean");

        string 
        pattern
        format
        date-time email
        hostname ipv4
        ipv6 uri
        integer number
        multipleOf minimum, maximum, exclusiveMinimum and exclusiveMaximum        
        object properties
        additionalProperties required 
        minProperties maxProperties 
        dependencies
        ...        
    array...
    boolean...
        null...    
*/

    }

    private Consts() {
    }

}
