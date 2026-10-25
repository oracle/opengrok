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
 * Copyright (c) 2026, Siyabend Urun <urunsiyabend@gmail.com>.
 */
package org.opengrok.indexer.analysis.groovy;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a container for a set of Apache Groovy reserved words.
 *
 * <p>The keyword list is taken verbatim from the Apache Groovy Language
 * Specification, section &quot;Keywords&quot;
 * (<a href="https://groovy-lang.org/syntax.html#_keywords">groovy-lang.org/syntax.html#_keywords</a>):
 * <ul>
 *   <li>Table 1 &mdash; Reserved Keywords (cannot be used as identifiers).</li>
 *   <li>Table 2 &mdash; Contextual Keywords (reserved only in certain
 *       syntactic positions, e.g. {@code var}, {@code record},
 *       {@code permits}, {@code sealed}, {@code trait}, {@code yields},
 *       {@code as}, {@code in}).</li>
 *   <li>Table 3 &mdash; Other reserved words (boolean / null literals and
 *       primitive type names).</li>
 * </ul>
 *
 * <p>Reserved keywords that the specification flags as &quot;not currently in
 * use&quot; ({@code const}, {@code goto}, {@code strictfp},
 * {@code threadsafe}) are still highlighted, because the Groovy grammar
 * disallows them as identifiers.
 */
public class Consts {

    static final Set<String> kwd = new HashSet<>();

    static {
        // ---- Apache Groovy syntax.html Table 1: Reserved Keywords ----
        kwd.add("abstract"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("assert"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("break"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("case"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("catch"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("class"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("const"); // Apache Groovy - Reserved Keyword (Table 1, not currently in use)
        kwd.add("continue"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("def"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("default"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("do"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("else"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("enum"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("extends"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("final"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("finally"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("for"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("goto"); // Apache Groovy - Reserved Keyword (Table 1, not currently in use)
        kwd.add("if"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("implements"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("import"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("instanceof"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("interface"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("native"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("new"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("non-sealed"); // Apache Groovy - Reserved Keyword (Table 1, Groovy 4.0+)
        kwd.add("null"); // Apache Groovy - Reserved Keyword (Table 1, also listed in Table 3 as null literal)
        kwd.add("package"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("private"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("protected"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("public"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("return"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("static"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("strictfp"); // Apache Groovy - Reserved Keyword (Table 1, not currently in use)
        kwd.add("super"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("switch"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("synchronized"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("this"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("threadsafe"); // Apache Groovy - Reserved Keyword (Table 1, not currently in use)
        kwd.add("throw"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("throws"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("transient"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("try"); // Apache Groovy - Reserved Keyword (Table 1)
        kwd.add("while"); // Apache Groovy - Reserved Keyword (Table 1)

        // ---- Apache Groovy syntax.html Table 2: Contextual Keywords ----
        kwd.add("as"); // Apache Groovy - Contextual Keyword (Table 2)
        kwd.add("in"); // Apache Groovy - Contextual Keyword (Table 2)
        kwd.add("permits"); // Apache Groovy - Contextual Keyword (Table 2, Groovy 4.0+)
        kwd.add("record"); // Apache Groovy - Contextual Keyword (Table 2, Groovy 4.0+)
        kwd.add("sealed"); // Apache Groovy - Contextual Keyword (Table 2, Groovy 4.0+)
        kwd.add("trait"); // Apache Groovy - Contextual Keyword (Table 2)
        kwd.add("var"); // Apache Groovy - Contextual Keyword (Table 2, Groovy 3.0+)
        kwd.add("yields"); // Apache Groovy - Contextual Keyword (Table 2, Groovy 4.0+)

        // ---- Apache Groovy syntax.html Table 3: Other Reserved Words ----
        kwd.add("true"); // Apache Groovy - Other Reserved Word (Table 3, boolean literal)
        kwd.add("false"); // Apache Groovy - Other Reserved Word (Table 3, boolean literal)
        kwd.add("boolean"); // Apache Groovy - Other Reserved Word (Table 3, primitive type)
        kwd.add("byte"); // Apache Groovy - Other Reserved Word (Table 3, primitive type)
        kwd.add("char"); // Apache Groovy - Other Reserved Word (Table 3, primitive type)
        kwd.add("short"); // Apache Groovy - Other Reserved Word (Table 3, primitive type)
        kwd.add("int"); // Apache Groovy - Other Reserved Word (Table 3, primitive type)
        kwd.add("long"); // Apache Groovy - Other Reserved Word (Table 3, primitive type)
        kwd.add("float"); // Apache Groovy - Other Reserved Word (Table 3, primitive type)
        kwd.add("double"); // Apache Groovy - Other Reserved Word (Table 3, primitive type)
    }

    /** Private to enforce static. */
    private Consts() {
    }
}