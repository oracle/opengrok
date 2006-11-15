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
 * ident	"@(#)Consts.java 1.1     05/11/11 SMI"
 */

package org.opensolaris.opengrok.analysis.c;

import java.util.*;

/**
  * Holds static hash set containing the C keywords
  */
public class Consts{
    public static final HashSet kwd = new HashSet() ;
    static {
        kwd.add( "throws" );
        kwd.add( "import" );
        kwd.add( "package" );
        kwd.add( "final" );
        kwd.add( "ident" );
        kwd.add( "ifndef" );
        kwd.add( "defined" );
        kwd.add( "endif" );
        kwd.add( "auto" );
        kwd.add( "bool" );
        kwd.add( "break" );
        kwd.add( "case" );
        kwd.add( "catch" );
        kwd.add( "char" );
        kwd.add( "const" );
        kwd.add( "continue" );
        kwd.add( "default" );
        kwd.add( "delete" );
        kwd.add( "do" );
        kwd.add( "double" );
        kwd.add( "else" );
        kwd.add( "enum" );
        kwd.add( "extern" );
        kwd.add( "float" );
        kwd.add( "for" );
        kwd.add( "friend" );
        kwd.add( "goto" );
        kwd.add( "if" );
        kwd.add( "inline" );
        kwd.add( "int" );
        kwd.add( "long" );
        kwd.add( "namespace" );
        kwd.add( "new" );
        kwd.add( "private" );
        kwd.add( "protected" );
        kwd.add( "public" );
        kwd.add( "redeclared" );
        kwd.add( "register" );
        kwd.add( "return" );
        kwd.add( "short" );
        kwd.add( "signed" );
        kwd.add( "sizeof" );
        kwd.add( "static" );
        kwd.add( "struct" );
        kwd.add( "class" );
        kwd.add( "switch" );
        kwd.add( "template" );
        kwd.add( "this" );
        kwd.add( "try" );
        kwd.add( "typedef" );
        kwd.add( "union" );
        kwd.add( "unsigned" );
        kwd.add( "using" );
        kwd.add( "virtual" );
        kwd.add( "void" );
        kwd.add( "volatile" );
        kwd.add( "while" );
        kwd.add( "operator" );
        kwd.add( "true" );
        kwd.add( "false" );
        kwd.add( "throw" );
        kwd.add( "include" );
        kwd.add( "define" );
        kwd.add( "ifdef" );
        kwd.add( "pragma" );
    }
}
