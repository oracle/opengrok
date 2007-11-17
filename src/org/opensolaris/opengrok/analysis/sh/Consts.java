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

import java.util.HashSet;
/**
 * Shell keyword hash
 */
public class Consts{
    public static final HashSet<String> shkwd = new HashSet<String>() ;
    static {
        // Built-in shell commands mentioned in shell_builtins(1)
        shkwd.add( ":" );
        shkwd.add( "." );
        shkwd.add( "alias" );
        shkwd.add( "bg" );
        shkwd.add( "break" );
        shkwd.add( "case" );
        shkwd.add( "cd" );
        shkwd.add( "chdir" );
        shkwd.add( "continue" );
        shkwd.add( "dirs" );
        shkwd.add( "echo" );
        shkwd.add( "eval" );
        shkwd.add( "exec" );
        shkwd.add( "exit" );
        shkwd.add( "export" );
        shkwd.add( "false" );
        shkwd.add( "fc" );
        shkwd.add( "fg" );
        shkwd.add( "for" );
        shkwd.add( "foreach" );
        shkwd.add( "function" );
        shkwd.add( "getopts" );
        shkwd.add( "glob" );
        shkwd.add( "goto" );
        shkwd.add( "hash" );
        shkwd.add( "hashstat" );
        shkwd.add( "history" );
        shkwd.add( "if" );
        shkwd.add( "jobs" );
        shkwd.add( "kill" );
        shkwd.add( "let" );
        shkwd.add( "limit" );
        shkwd.add( "login" );
        shkwd.add( "logout" );
        shkwd.add( "nice" );
        shkwd.add( "newgrp" );
        shkwd.add( "nohup" );
        shkwd.add( "notify" );
        shkwd.add( "onintr" );
        shkwd.add( "popd" );
        shkwd.add( "print" );
        shkwd.add( "pushd" );
        shkwd.add( "pwd" );
        shkwd.add( "read" );
        shkwd.add( "readonly" );
        shkwd.add( "rehash" );
        shkwd.add( "repeat" );
        shkwd.add( "return" );
        shkwd.add( "select" );
        shkwd.add( "set" );
        shkwd.add( "setenv" );
        shkwd.add( "shift" );
        shkwd.add( "source" );
        shkwd.add( "stop" );
        shkwd.add( "suspend" );
        shkwd.add( "switch" );
        shkwd.add( "test" );
        shkwd.add( "time" );
        shkwd.add( "times" );
        shkwd.add( "trap" );
        shkwd.add( "true" );
        shkwd.add( "type" );
        shkwd.add( "typeset" );
        shkwd.add( "ulimit" );
        shkwd.add( "umask" );
        shkwd.add( "unalias" );
        shkwd.add( "unhash" );
        shkwd.add( "unlimit" );
        shkwd.add( "unset" );
        shkwd.add( "unsetenv" );
        shkwd.add( "until" );
        shkwd.add( "wait" );
        shkwd.add( "whence" );
        shkwd.add( "while" );

        // More keywords
        shkwd.add( "autoload" );
        shkwd.add( "builtin" );
        shkwd.add( "command" );
        shkwd.add( "redirect" );
        shkwd.add( "my" );
        shkwd.add( "next" );
        shkwd.add( "else" );
        shkwd.add( "elif" );
        shkwd.add( "elsif" );
        shkwd.add( "then" );
        shkwd.add( "fi" );
        shkwd.add( "do" );
        shkwd.add( "done" );
        shkwd.add( "esac" );
        shkwd.add( "sub" );
        shkwd.add( "require" );
        shkwd.add( "use" );
        shkwd.add( "end" );
        shkwd.add( "declaration" );
        shkwd.add( "local" );
        shkwd.add( "complex" );
        shkwd.add( "int" );
        shkwd.add( "integer" );
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
        shkwd.add( "nameref" );
    }
}
