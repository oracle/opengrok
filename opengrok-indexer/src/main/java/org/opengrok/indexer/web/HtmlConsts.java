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
 * Copyright (c) 2017, 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.web;

/**
 * Represents a container class for string constants related to production of
 * HTML.
 */
public class HtmlConsts {
    public static final String SPAN_A = "<span class=\"a\">";
    public static final String SPAN_B = "<span class=\"b\">";
    public static final String SPAN_C = "<span class=\"c\">";
    public static final String SPAN_D = "<span class=\"d\">";
    public static final String SPAN_N = "<span class=\"n\">";
    public static final String SPAN_S = "<span class=\"s\">";
    public static final String ZSPAN = "</span>";

    public static final String SPAN_FMT = "<span class=\"%s\">";

    public static final String AUTHOR_CLASS = "a";
    public static final String BOLD_CLASS = "b";
    public static final String COMMENT_CLASS = "c";
    public static final String DELETED_CLASS = "d";
    public static final String MACRO_CLASS = "xm";
    public static final String NUMBER_CLASS = "n";
    public static final String STRING_CLASS = "s";

    public static final String B = "<b>";
    public static final String ZB = "</b>";

    public static final String BR = "<br/>";

    public static final String HELLIP = "&hellip;";

    /** Private to enforce static. */
    private HtmlConsts() {
    }
}
