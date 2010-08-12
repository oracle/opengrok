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
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis.perl;

import java.util.HashSet;
import java.util.Set;

/**
  * Holds static hash set containing the Perl keywords
  */
public class Consts{
    public static final Set<String> kwd = new HashSet<String>() ;
    static {
        kwd.add( "use" );
        kwd.add("require");
        kwd.add("my");

        kwd.add( "if" );
        kwd.add( "else" );
        kwd.add( "elsif" );
        kwd.add( "while" );
        kwd.add( "for" );
        kwd.add( "continue" );
        kwd.add("foreach");
        kwd.add("until");
        kwd.add("unless");
        kwd.add("do");
        kwd.add("eval");
        kwd.add("when");
        kwd.add("next");
        kwd.add("goto");
        kwd.add("last");
        kwd.add("redo");
        kwd.add("sub");

        kwd.add("given"); //Perl 5.10
        kwd.add("break");

        kwd.add("die");
        kwd.add("print");        
    }
}
