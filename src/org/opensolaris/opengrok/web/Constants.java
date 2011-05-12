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

package org.opensolaris.opengrok.web;

/**
 * web specific constants (web.xml has the only dup of this)
 * + some of the constants of data root
 * @author Lubos Kosco
 */
public final class Constants {

    //full name of cross reference prefix
    public static final String xrefP="/xref";
    //short cut for easier recognition of servlets in jsp pages, TODO redesign to be more intuitive
    public static final String xrefS="/xr";
    public static final String moreP="/more";
    public static final String moreS="/mo";
    public static final String diffP="/diff";
    public static final String diffS="/di";
    public static final String histP="/hist";
    public static final String histL="/history";
    public static final String histS="/hi";
    public static final String rssP="/rss";
    public static final String rawP="/raw";
    //full blown search from main page or top bar
    public static final String searchP="/search";
    //search from cross reference, can lead to direct match(which opens directly)
    public static final String searchR="/s";
}
