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

package org.opensolaris.opengrok.analysis.php;

import java.util.HashSet;
import java.util.Set;

/**
  * Holds static hash set containing the Perl keywords
  */
public class Consts{
    public static final Set<String> kwd = new HashSet<String>() ;
    static {
        //Keywords
        kwd.add("abstract"); //As of PHP5
        kwd.add("and");
        kwd.add("array");
        kwd.add("as");
        kwd.add("break");
        kwd.add("callable");
        kwd.add("case");
        kwd.add("catch"); //As of PHP5
        kwd.add("cfunction"); // PHP4 Only
        kwd.add("class");
        kwd.add("clone"); //As of PHP5
        kwd.add("const");
        kwd.add("continue");
        kwd.add("declare");
        kwd.add("default");
        kwd.add("do");
        kwd.add("else");
        kwd.add("elseif");
        kwd.add("enddeclare");
        kwd.add("endfor");
        kwd.add("endforeach");
        kwd.add("endif");
        kwd.add("endswitch");
        kwd.add("endwhile");
        kwd.add("extends");
        kwd.add("final"); //As of PHP5
        kwd.add("for");
        kwd.add("foreach");
        kwd.add("function");
        kwd.add("global");
        kwd.add("goto"); //As of PHP5.3
        kwd.add("instanceof");
        kwd.add("if");
        kwd.add("implements"); //As of PHP5
        kwd.add("insteadof"); //As of PHP5.4
        kwd.add("interface"); //As of PHP5
        kwd.add("interfaceof"); //As of PHP5
        kwd.add("namespace"); //As of PHP5
        kwd.add("new");
        kwd.add("old_function"); //PHP4 Only
        kwd.add("or");
        kwd.add("private"); //As of PHP5
        kwd.add("protected"); //As of PHP5
        kwd.add("public"); //As of PHP5
        kwd.add("static");
        kwd.add("switch");
        kwd.add("throw"); //As of PHP5
        kwd.add("trait"); //As of PHP5.4
        kwd.add("try"); //As of PHP5
        kwd.add("use");
        kwd.add("var");
        kwd.add("while");
        kwd.add("xor");

        //Constants
        kwd.add("__CLASS__");
        kwd.add("__DIR__"); //As of PHP5.3
        kwd.add("__FILE__");
        kwd.add("__FUNCTION__");
        kwd.add("__LINE__");
        kwd.add("__METHOD__");
        kwd.add("__NAMESPACE__");
        kwd.add("__TRAIT__"); //As of PHP5.4

        //Constructs
        kwd.add("die");
        kwd.add("echo");
        kwd.add("empty");
        kwd.add("exit");
        kwd.add("eval");
        kwd.add("include");
        kwd.add("include_once");
        kwd.add("isset");
        kwd.add("list");
        kwd.add("require");
        kwd.add("require_once");
        kwd.add("return");
        kwd.add("print");
        kwd.add("unset");

        //Misc
        kwd.add("__halt_compiler");
    }
}
