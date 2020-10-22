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
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

public class XrefStyle {

    /**
     * Name of the style definition as given by CTags.
     */
    final String name;
    /**
     * Class name used by the style sheets when rendering the xref.
     */
    final String ssClass;
    /**
     * The title of the section to which this type belongs, or {@code null}
     * if this type should not be listed in the navigation panel.
     */
    final String title;

    XrefStyle(String name, String ssClass, String title) {
        this.name = name;
        this.ssClass = ssClass;
        this.title = title;
    }

    /**
     * Description of styles to use for different types of definitions.
     */
    public static final XrefStyle[] DEFINITION_STYLES = {
        new XrefStyle("macro", "xm", "Macro"),
        new XrefStyle("argument", "xa", null),
        new XrefStyle("local", "xl", null),
        new XrefStyle("variable", "xv", "Variable"),
        new XrefStyle("class", "xc", "Class"),
        new XrefStyle("package", "xp", "Package"),
        new XrefStyle("interface", "xi", "Interface"),
        new XrefStyle("namespace", "xn", "Namespace"),
        new XrefStyle("enumerator", "xer", null),
        new XrefStyle("enum", "xe", "Enum"),
        new XrefStyle("struct", "xs", "Struct"),
        new XrefStyle("typedefs", "xts", null),
        new XrefStyle("typedef", "xt", "Typedef"),
        new XrefStyle("union", "xu", null),
        new XrefStyle("field", "xfld", null),
        new XrefStyle("member", "xmb", null),
        new XrefStyle("function", "xf", "Function"),
        new XrefStyle("method", "xmt", "Method"),
        new XrefStyle("subroutine", "xsr", "Subroutine"),
        new XrefStyle("label", "xlbl", "Label"),
        new XrefStyle("procedure", "xf", "Procedure")
    };

    /**
     * Get the style description for a definition type.
     * @param type the definition type
     * @return the style of a definition type, or {@code null} if no style is
     * defined for the type
     * @see #DEFINITION_STYLES
     */
    public static XrefStyle getStyle(String type) {
        for (XrefStyle style : DEFINITION_STYLES) {
            if (type.startsWith(style.name)) {
                return style;
            }
        }
        return null;
    }
}
