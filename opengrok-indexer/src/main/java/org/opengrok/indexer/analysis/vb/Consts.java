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
 * Copyright (c) 2007, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.vb;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("PMD.AvoidThrowingRawExceptionTypes")
public final class Consts {

    public static final Set<String> reservedKeywords;
    public static final Set<String> directives;

    static {
        HashSet<String> kwds = new HashSet<>();
        populateKeywordSet(kwds);
        reservedKeywords = Collections.unmodifiableSet(kwds);

        // VB lang-reference/keywords
        directives = Set.of("#const", "#else", "#elseif", "#end", "#if");
    }

    private Consts() {
        // Util class, can not be constructed.
    }

    private static void populateKeywordSet(Set<String> kwd) {
        kwd.clear();
        kwd.add("addhandler"); // Original vb.Consts but now l-case
        kwd.add("addressof"); // Original vb.Consts but now l-case
        kwd.add("alias"); // Original vb.Consts but now l-case
        kwd.add("and"); // Original vb.Consts but now l-case
        kwd.add("andalso"); // Original vb.Consts but now l-case
        kwd.add("as"); // Original vb.Consts but now l-case
        kwd.add("boolean"); // Original vb.Consts but now l-case
        kwd.add("byref"); // Original vb.Consts but now l-case
        kwd.add("byte"); // Original vb.Consts but now l-case
        kwd.add("byval"); // Original vb.Consts but now l-case
        kwd.add("call"); // Original vb.Consts but now l-case
        kwd.add("case"); // Original vb.Consts but now l-case
        kwd.add("catch"); // Original vb.Consts but now l-case
        kwd.add("cbool"); // Original vb.Consts but now l-case
        kwd.add("cbyte"); // Original vb.Consts but now l-case
        kwd.add("cchar"); // Original vb.Consts but now l-case
        kwd.add("cdate"); // Original vb.Consts but now l-case
        kwd.add("cdec"); // Original vb.Consts but now l-case
        kwd.add("cdbl"); // Original vb.Consts but now l-case
        kwd.add("char"); // Original vb.Consts but now l-case
        kwd.add("cint"); // Original vb.Consts but now l-case
        kwd.add("class"); // Original vb.Consts but now l-case
        kwd.add("clng"); // Original vb.Consts but now l-case
        kwd.add("cobj"); // Original vb.Consts but now l-case
        kwd.add("const"); // Original vb.Consts but now l-case
        kwd.add("continue"); // Original vb.Consts but now l-case
        kwd.add("csbyte"); // Original vb.Consts but now l-case
        kwd.add("cshort"); // Original vb.Consts but now l-case
        kwd.add("csng"); // Original vb.Consts but now l-case
        kwd.add("cstr"); // Original vb.Consts but now l-case
        kwd.add("ctype"); // Original vb.Consts but now l-case
        kwd.add("cuint"); // Original vb.Consts but now l-case
        kwd.add("culng"); // Original vb.Consts but now l-case
        kwd.add("cushort"); // Original vb.Consts but now l-case
        kwd.add("date"); // Original vb.Consts but now l-case
        kwd.add("decimal"); // Original vb.Consts but now l-case
        kwd.add("declare"); // Original vb.Consts but now l-case
        kwd.add("default"); // Original vb.Consts but now l-case
        kwd.add("delegate"); // Original vb.Consts but now l-case
        kwd.add("dim"); // Original vb.Consts but now l-case
        kwd.add("directcast"); // Original vb.Consts but now l-case
        kwd.add("do"); // Original vb.Consts but now l-case
        kwd.add("double"); // Original vb.Consts but now l-case
        kwd.add("each"); // Original vb.Consts but now l-case
        kwd.add("else"); // Original vb.Consts but now l-case
        kwd.add("elseif"); // Original vb.Consts but now l-case
        kwd.add("end"); // Original vb.Consts but now l-case
        kwd.add("endif"); // Original vb.Consts but now l-case
        kwd.add("enum"); // Original vb.Consts but now l-case
        kwd.add("erase"); // Original vb.Consts but now l-case
        kwd.add("error"); // Original vb.Consts but now l-case
        kwd.add("event"); // Original vb.Consts but now l-case
        kwd.add("exit"); // Original vb.Consts but now l-case
        kwd.add("false"); // Original vb.Consts but now l-case
        kwd.add("finally"); // Original vb.Consts but now l-case
        kwd.add("for"); // Original vb.Consts but now l-case
        kwd.add("friend"); // Original vb.Consts but now l-case
        kwd.add("function"); // Original vb.Consts but now l-case
        kwd.add("get"); // Original vb.Consts but now l-case
        kwd.add("gettype"); // Original vb.Consts but now l-case
        kwd.add("global"); // Original vb.Consts but now l-case
        kwd.add("gosub"); // Original vb.Consts but now l-case
        kwd.add("goto"); // Original vb.Consts but now l-case
        kwd.add("handles"); // Original vb.Consts but now l-case
        kwd.add("if"); // Original vb.Consts but now l-case
        kwd.add("implements"); // Original vb.Consts but now l-case
        kwd.add("imports"); // Original vb.Consts but now l-case
        kwd.add("in"); // Original vb.Consts but now l-case
        kwd.add("inherits"); // Original vb.Consts but now l-case
        kwd.add("integer"); // Original vb.Consts but now l-case
        kwd.add("interface"); // Original vb.Consts but now l-case
        kwd.add("is"); // Original vb.Consts but now l-case
        kwd.add("isnot"); // Original vb.Consts but now l-case
        kwd.add("let"); // Original vb.Consts but now l-case
        kwd.add("lib"); // Original vb.Consts but now l-case
        kwd.add("like"); // Original vb.Consts but now l-case
        kwd.add("long"); // Original vb.Consts but now l-case
        kwd.add("loop"); // Original vb.Consts but now l-case
        kwd.add("me"); // Original vb.Consts but now l-case
        kwd.add("mod"); // Original vb.Consts but now l-case
        kwd.add("module"); // Original vb.Consts but now l-case
        kwd.add("mustinherit"); // Original vb.Consts but now l-case
        kwd.add("mustoverride"); // Original vb.Consts but now l-case
        kwd.add("mybase"); // Original vb.Consts but now l-case
        kwd.add("myclass"); // Original vb.Consts but now l-case
        kwd.add("namespace"); // Original vb.Consts but now l-case
        kwd.add("narrowing"); // Original vb.Consts but now l-case
        kwd.add("new"); // Original vb.Consts but now l-case
        kwd.add("next"); // Original vb.Consts but now l-case
        kwd.add("not"); // Original vb.Consts but now l-case
        kwd.add("nothing"); // Original vb.Consts but now l-case
        kwd.add("notinheritable"); // Original vb.Consts but now l-case
        kwd.add("notoverridable"); // Original vb.Consts but now l-case
        kwd.add("object"); // Original vb.Consts but now l-case
        kwd.add("of"); // Original vb.Consts but now l-case
        kwd.add("on"); // Original vb.Consts but now l-case
        kwd.add("operator"); // Original vb.Consts but now l-case
        kwd.add("option"); // Original vb.Consts but now l-case
        kwd.add("optional"); // Original vb.Consts but now l-case
        kwd.add("or"); // Original vb.Consts but now l-case
        kwd.add("orelse"); // Original vb.Consts but now l-case
        kwd.add("overloads"); // Original vb.Consts but now l-case
        kwd.add("overridable"); // Original vb.Consts but now l-case
        kwd.add("overrides"); // Original vb.Consts but now l-case
        kwd.add("paramarray"); // Original vb.Consts but now l-case
        kwd.add("partial"); // Original vb.Consts but now l-case
        kwd.add("private"); // Original vb.Consts but now l-case
        kwd.add("property"); // Original vb.Consts but now l-case
        kwd.add("protected"); // Original vb.Consts but now l-case
        kwd.add("public"); // Original vb.Consts but now l-case
        kwd.add("raiseevent"); // Original vb.Consts but now l-case
        kwd.add("readonly"); // Original vb.Consts but now l-case
        kwd.add("redim"); // Original vb.Consts but now l-case
        kwd.add("rem"); // Original vb.Consts but now l-case
        kwd.add("removehandler"); // Original vb.Consts but now l-case
        kwd.add("resume"); // Original vb.Consts but now l-case
        kwd.add("return"); // Original vb.Consts but now l-case
        kwd.add("sbyte"); // Original vb.Consts but now l-case
        kwd.add("select"); // Original vb.Consts but now l-case
        kwd.add("set"); // Original vb.Consts but now l-case
        kwd.add("shadows"); // Original vb.Consts but now l-case
        kwd.add("shared"); // Original vb.Consts but now l-case
        kwd.add("short"); // Original vb.Consts but now l-case
        kwd.add("single"); // Original vb.Consts but now l-case
        kwd.add("static"); // Original vb.Consts but now l-case
        kwd.add("step"); // Original vb.Consts but now l-case
        kwd.add("stop"); // Original vb.Consts but now l-case
        kwd.add("string"); // Original vb.Consts but now l-case
        kwd.add("structure"); // Original vb.Consts but now l-case
        kwd.add("sub"); // Original vb.Consts but now l-case
        kwd.add("synclock"); // Original vb.Consts but now l-case
        kwd.add("then"); // Original vb.Consts but now l-case
        kwd.add("throw"); // Original vb.Consts but now l-case
        kwd.add("to"); // Original vb.Consts but now l-case
        kwd.add("true"); // Original vb.Consts but now l-case
        kwd.add("try"); // Original vb.Consts but now l-case
        kwd.add("trycast"); // Original vb.Consts but now l-case
        kwd.add("typeof"); // Original vb.Consts but now l-case
        kwd.add("variant"); // Original vb.Consts but now l-case
        kwd.add("wend"); // Original vb.Consts but now l-case
        kwd.add("uinteger"); // Original vb.Consts but now l-case
        kwd.add("ulong"); // Original vb.Consts but now l-case
        kwd.add("ushort"); // Original vb.Consts but now l-case
        kwd.add("using"); // Original vb.Consts but now l-case
        kwd.add("when"); // Original vb.Consts but now l-case
        kwd.add("while"); // Original vb.Consts but now l-case
        kwd.add("widening"); // Original vb.Consts but now l-case
        kwd.add("with"); // Original vb.Consts but now l-case
        kwd.add("withevents"); // Original vb.Consts but now l-case
        kwd.add("writeonly"); // Original vb.Consts but now l-case
        kwd.add("xor"); // Original vb.Consts but now l-case

        kwd.add("getxmlnamespace"); // VB lang-reference/keywords l-case
        kwd.add("out"); // VB lang-reference/keywords l-case
    }
}
