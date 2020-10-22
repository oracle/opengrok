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
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.erlang;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Erlang keywords.
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();
    public static final Set<String> modules_kwd = new HashSet<>();
    static {
        kwd.add("after"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("and"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("andalso"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("band"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("begin"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("bnot"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("bor"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("bsl"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("bsr"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("bxor"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("case"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("catch"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("cond"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("div"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("end"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("fun"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("if"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("let"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("not"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("of"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("or"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("orelse"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("receive"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("rem"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("try"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("when"); // Ref. 9.1 "1.5 Reserved Words"
        kwd.add("xor"); // Ref. 9.1 "1.5 Reserved Words"

        kwd.add("query"); // pre-existing here of unknown provenance

        modules_kwd.add("behavior"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("behaviour"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("callback"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("compile"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("define"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("export"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("file"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("import"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("include"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("module"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("on_load"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("record"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("spec"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("type"); // Ref. 9.1 "5.2 Module Attributes"
        modules_kwd.add("vsn"); // Ref. 9.1 "5.2 Module Attributes"
    }

    private Consts() {
    }

}
