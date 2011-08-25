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
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis.javascript;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Javascript keywords
 * 
 * * ECMA-262 5.1 Edition June 2011
 * 
 */
public class Consts{
    public static final Set<String> kwd = new HashSet<String>() ;
    static {
        //constants
        kwd.add("true");
        kwd.add("false");
        kwd.add("null");
        //builtins
        kwd.add("Array");
        kwd.add("Boolean");
        kwd.add("Date");
        kwd.add("Function");
        kwd.add("Math");
        kwd.add("Number");
        kwd.add("Object");
        kwd.add("RegExp");
        kwd.add("String");                
        //keywords
        kwd.add( "break" );
        kwd.add( "case" );
        kwd.add( "catch" );
        kwd.add( "continue" );
        kwd.add( "debugger" );
        kwd.add( "default" );
        kwd.add( "delete" );
        kwd.add( "do" );
        kwd.add( "else" );
        kwd.add( "finally" );
        kwd.add( "for" );
        kwd.add( "function" );        
        kwd.add( "if" );
        kwd.add( "in" );
        kwd.add( "instanceof" );
        kwd.add( "new" );
        kwd.add( "return" );
        kwd.add( "switch" );
        kwd.add("this");
        kwd.add( "throw" );
        kwd.add( "try" );
        kwd.add( "typeof" );
        kwd.add( "var" );        
        kwd.add( "void" );        
        kwd.add( "while" );
        kwd.add( "with" );
        //future reserved
        kwd.add( "class" );
        kwd.add( "const" );
        kwd.add( "enum" );
        kwd.add( "export" );
        kwd.add( "extends" );
        kwd.add( "import" );
        kwd.add( "super" );
        //strict future reserved
        kwd.add( "implements" );
        kwd.add( "interface" );
        kwd.add( "let" );
        kwd.add( "package" );
        kwd.add( "private" );
        kwd.add( "protected" );
        kwd.add( "public" );
        kwd.add( "static" );
        kwd.add( "yield" );
        
    }
}
