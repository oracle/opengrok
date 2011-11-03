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

package org.opensolaris.opengrok.analysis.csharp;

import java.util.HashSet;
import java.util.Set;

/**
  *  C# keywords
 * @author Christoph Hofmann
  */
public class Consts{
    public static final Set<String> kwd = new HashSet<String>() ;
    static {
        // C# Keywords
        kwd.add("abstract");
        kwd.add("as");
        kwd.add("base");
        kwd.add("bool");
        kwd.add("break");
        kwd.add("byte");
        kwd.add("case");
        kwd.add("catch");
        kwd.add("char");
        kwd.add("checked");
        kwd.add("class");
        kwd.add("const");
        kwd.add("continue");
        kwd.add("decimal");
        kwd.add("default");
        kwd.add("delegate");
        kwd.add("do");
        kwd.add("double");
        kwd.add("else");
        kwd.add("enum");
        kwd.add("event");
        kwd.add("explicit");
        kwd.add("extern");
        kwd.add("false");
        kwd.add("finally");
        kwd.add("fixed");
        kwd.add("float");
        kwd.add("for");
        kwd.add("foreach");
        kwd.add("goto");
        kwd.add("if");
        kwd.add("implicit");
        kwd.add("in");
        kwd.add("int");
        kwd.add("interface");
        kwd.add("internal");
        kwd.add("is");
        kwd.add("lock");
        kwd.add("long");
        kwd.add("namespace");
        kwd.add("new");
        kwd.add("null");
        kwd.add("object");
        kwd.add("operator");
        kwd.add("out");
        kwd.add("override");
        kwd.add("params");
        kwd.add("private");
        kwd.add("protected");
        kwd.add("public");
        kwd.add("readonly");
        kwd.add("ref");
        kwd.add("return");
        kwd.add("sbyte");
        kwd.add("seale");
        kwd.add("short");
        kwd.add("sizeof");
        kwd.add("stackalloc");
        kwd.add("static");
        kwd.add("string");
        kwd.add("struct");
        kwd.add("switch");
        kwd.add("this");
        kwd.add("throw");
        kwd.add("true");
        kwd.add("try");
        kwd.add("typeof");
        kwd.add("uint");
        kwd.add("ulong");
        kwd.add("unchecked");
        kwd.add("unsafe");
        kwd.add("ushort");
        kwd.add("using");
        kwd.add("virtual");
        kwd.add("void");
        kwd.add("volatile");
        kwd.add("while");
        //C# Contextual Keywords
        kwd.add("add");
        kwd.add("alias");
        kwd.add("ascending");
        kwd.add("descending");
        kwd.add("dynamic");
        kwd.add("from");
        kwd.add("get");
        kwd.add("global");
        kwd.add("group");
        kwd.add("into");
        kwd.add("join");
        kwd.add("let");
        kwd.add("orderby");
        kwd.add("remove");
        kwd.add("select");
        kwd.add("set");
        kwd.add("partial");
        kwd.add("value");
        kwd.add("var");
        kwd.add("where");
        kwd.add("yield");
        //C# Preprocessor Directives
        kwd.add("#if");
        kwd.add("#else");
        kwd.add("#elif");
        kwd.add("#endif");
        kwd.add("#define");
        kwd.add("#undef");
        kwd.add("#warning");
        kwd.add("#error");
        kwd.add("#line");
        kwd.add("#region");
        kwd.add("#endregion");
        kwd.add("#pragma");
        kwd.add("#pragma warning");
        kwd.add("#pragma checksum");
        kwd.add("#pragma warning restore");
        kwd.add("#pragma warning disable");
    }
}


