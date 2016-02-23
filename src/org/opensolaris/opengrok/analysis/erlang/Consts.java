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
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis.erlang;

import java.util.HashSet;
import java.util.Set;

/**
  * Holds static hash set containing the Erlang keywords
  */
public class Consts{
    public static final Set<String> kwd = new HashSet<String>() ;
    static {
        kwd.add("after");
        kwd.add("begin");
        kwd.add("case");
        kwd.add("try");
        kwd.add("cond");
        kwd.add("catch");
        kwd.add("andalso");
        kwd.add("orelse");
        kwd.add("end");
        kwd.add("fun");
        kwd.add("if");
        kwd.add("let");
        kwd.add("of");
        kwd.add("query");
        kwd.add("receive");
        kwd.add("when");
        kwd.add("bnot");
        kwd.add("not");
        kwd.add("div");
        kwd.add("rem");
        kwd.add("band");
        kwd.add("and");
        kwd.add("bor");
        kwd.add("bxor");
        kwd.add("bsl");
        kwd.add("bsr");
        kwd.add("or");
        kwd.add("xor");
    }
}
