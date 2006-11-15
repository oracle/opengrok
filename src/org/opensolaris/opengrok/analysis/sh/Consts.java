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
 * ident	"@(#)Consts.java 1.2     05/12/01 SMI"
 */

package org.opensolaris.opengrok.analysis.sh;

import java.util.*;
/**
 * Shell keyword hash
 */
public class Consts{
    public static final HashSet shkwd = new HashSet() ;
    static {
        shkwd.add( "my" );
        shkwd.add( "next" );
        shkwd.add( "continue" );
        shkwd.add( "if" );
        shkwd.add( "else" );
        shkwd.add( "elif" );
        shkwd.add( "elsif" );
        shkwd.add( "then" );
        shkwd.add( "fi" );
        shkwd.add( "while" );
        shkwd.add( "do" );
        shkwd.add( "done" );
        shkwd.add( "for" );
        shkwd.add( "foreach" );
        shkwd.add( "case" );
        shkwd.add( "esac" );
        shkwd.add( "select" );
        shkwd.add( "time" );
        shkwd.add( "until" );
        shkwd.add( "function" );
        shkwd.add( "sub" );
        shkwd.add( "require" );
        shkwd.add( "use" );
        shkwd.add( "end" );
        shkwd.add( "declaration" );
        shkwd.add( "int" );
        shkwd.add( "char" );
        shkwd.add( "const" );
        shkwd.add( "bool" );
        shkwd.add( "boolean" );
        shkwd.add( "float" );
        shkwd.add( "double" );
        shkwd.add( "long" );
        shkwd.add( "struct" );
        shkwd.add( "void" );
        shkwd.add( "unsigned" );
    }
}
