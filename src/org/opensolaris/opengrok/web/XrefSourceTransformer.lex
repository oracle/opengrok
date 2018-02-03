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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.web;

import java.io.Writer;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
%%
%public
%class XrefSourceTransformer
%unicode
%type boolean
%eofval{
    return false;
%eofval}
%{
    private Writer out;

    /**
     * If set, this will have a leading and trailing slash (which might be the
     * same character for the root path). It's intended to substitute the
     * default {@code "/source/"} from
     * {@link RuntimeEnvironment#getUrlPrefix()}.
     */
    private String contextPath;

    /**
     * Sets the required target to write.
     * @param out a required instance
     */
    public void setWriter(Writer out) {
        if (out == null) {
            throw new IllegalArgumentException("`out' is null");
        }
        this.out = out;
    }

    /**
     * Sets the optional context path override.
     * @param path an optional instance
     */
    public void setContextPath(String path) {
        if (path == null || path.equals("source") || path.equals("/source") ||
            path.equals("/source/")) {
            this.contextPath = null;
        } else {
            StringBuilder pathBuilder = new StringBuilder();

            if (!path.startsWith("/")) pathBuilder.append("/");
            pathBuilder.append(path);
            this.contextPath = pathBuilder.toString();

            if (!this.contextPath.endsWith("/")) {
                pathBuilder.append("/");
                this.contextPath = pathBuilder.toString();
            }
        }
    }
%}

%%

/*
 * Matches a hyperlink to the static OpenGrok urlPrefix for possible
 * substitution.
 */
"href=\"/source/"    {
    if (contextPath == null) {
        out.write(yytext());
    } else {
        out.write("href=\"");
        out.write(contextPath);
    }
}

[^]    {
    out.write(yytext());
}
