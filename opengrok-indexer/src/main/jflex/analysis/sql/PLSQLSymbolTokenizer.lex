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
 * Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.sql;

import java.io.IOException;
import java.util.Set;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class PLSQLSymbolTokenizer
%extends JointSQLSymbolTokenizer
%unicode
%ignorecase
%int
%char
%include ../CommonLexer.lexh
%{
    /** Gets the keywords from {@link PLSQLConsts}. */
    @Override
    Set<String> getDialectKeywords() {
        return PLSQLConsts.KEYWORDS;
    }

    /**
     * Gets the constant value created by JFlex to represent
     * BRACKETED_COMMENT.
     */
    @Override
    int BRACKETED_COMMENT() {
        return BRACKETED_COMMENT;
    }

    /**
     * Gets the constant value created by JFlex to represent
     * SINGLE_LINE_COMMENT.
     */
    @Override
    int SINGLE_LINE_COMMENT() {
        return SINGLE_LINE_COMMENT;
    }
%}

%include ../Common.lexh
%include ../CommonURI.lexh
%include JointSQL.lexh
%include PLSQL.lexh
%include JointSQLProductions.lexh
